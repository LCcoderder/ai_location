import os
import math
import io
import requests
import torch
import torch.nn as nn
from torchvision import models, transforms
from PIL import Image
from flask import Flask, request, jsonify

app = Flask(__name__)

# ================= 1. 全局配置 =================
MODEL_PATH = './my_scenic_spot_model.pth'  # 你之前训练好的专属模型权重路径
NUM_CLASSES = 25  # 你的 25 个景点类别

# 高德地图 Web 服务 API Key (请去高德开放平台申请并替换)
AMAP_KEY = 'e908ae8fd5b9637fc9f830687684055d'

# 注意：这里的列表顺序必须和你训练时 train_dataset.classes 的顺序一模一样！
CLASS_NAMES = [
    "临潼骊山", "关中民俗艺术博物院", "华山风景区", "大唐不夜城",
    "大唐芙蓉园紫云楼", "汉景帝阳陵", "西安世博园长安塔", "西安兵马俑",
    "西安半坡博物馆", "西安华清宫", "西安回民街高家大院", "西安城墙南门",
    "西安大明宫丹凤门", "西安大雁塔", "西安小雁塔", "西安广仁寺",
    "西安昆明池", "西安曲江池", "西安楼观台", "西安碑林博物馆",
    "西安翠华山", "西安钟楼", "西安青龙寺", "西安鼓楼", "陕西历史博物馆"
]

# ================= 2. AI 模型初始化 =================
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"🖥️ 当前运行设备: {device}")

# 构建与训练时一致的 MobileNet V3-Small 模型
model = models.mobilenet_v3_small(weights=None)
# 自适应修改分类头
model.classifier[3] = nn.Linear(model.classifier[3].in_features, NUM_CLASSES)

if os.path.exists(MODEL_PATH):
    model.load_state_dict(torch.load(MODEL_PATH, map_location=device))
    model.to(device)
    model.eval()  # 设置为推理模式
    print("✅ AI 引擎启动成功，模型已加载！")
else:
    print("⚠️ 警告：未找到本地模型权重文件！请先完成训练。")

# 图像预处理流水线（与训练时严格一致，去掉随机翻转等数据增强）
transform = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
])


# ================= 3. 核心辅助计算函数 =================

def get_spot_location_amap(spot_name):
    """
    调用高德地图 API 动态获取景点经纬度
    """
    url = f"https://restapi.amap.com/v3/place/text?keywords={spot_name}&city=西安&output=json&key={AMAP_KEY}"
    try:
        response = requests.get(url, timeout=5).json()
        if response.get('status') == '1' and response.get('pois'):
            # 获取搜索结果中最匹配的第一个地点坐标
            location_str = response['pois'][0]['location']
            lon, lat = map(float, location_str.split(','))
            return lat, lon
    except Exception as e:
        print(f"❌ 高德 API 请求失败: {e}")
    return None, None


def calculate_distance(lat1, lon1, lat2, lon2):
    """
    使用 Haversine 公式计算两个 GPS 坐标点之间的球面距离（单位：米）
    """
    R = 6371000  # 地球平均半径，单位米
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)

    a = math.sin(delta_phi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2) ** 2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c


# ================= 4. 提供给外部调用的 API 接口 =================

@app.route('/api/verify_checkin', methods=['POST'])
def verify_checkin():
    """
    处理小程序的打卡请求，执行 LBS 距离校验 + AI 图像校验
    """
    try:
        # 1. 获取请求参数
        target_spot = request.form.get('target_spot')  # 用户声称自己所在的景点
        user_lat = float(request.form.get('latitude', 0.0))
        user_lon = float(request.form.get('longitude', 0.0))

        # 管理员在后台配置的允许打卡半径 (模拟从 Spring Boot 传过来的参数，默认设为 1000 米)
        allowable_radius = float(request.form.get('allowable_radius', 1000.0))

        if not target_spot:
            return jsonify({"code": 400, "msg": "缺少目标景点参数(target_spot)"})

        if 'image' not in request.files:
            return jsonify({"code": 400, "msg": "未上传图片文件"})

        file = request.files['image']

        # ---------------- 【第一重校验：LBS 动态距离校验】 ----------------
        print(f"📍 正在校验地理位置: 用户坐标({user_lat}, {user_lon}) -> 目标景点: {target_spot}")
        target_lat, target_lon = get_spot_location_amap(target_spot)

        if not target_lat:
            return jsonify({"code": 500, "msg": f"系统错误：未能通过高德地图找到【{target_spot}】的真实坐标"})

        distance = calculate_distance(user_lat, user_lon, target_lat, target_lon)

        # 🚀 1. 新增拦截：如果超过 10km (10000米)，直接打卡失败，不进入申诉 (status: 4)
        if distance > 10000:
            return jsonify({
                "code": 403,
                "msg": f"定位校验失败：您当前距离【{target_spot}】约 {int(distance)} 米，距离过远，打卡失败且无法申诉！",
                "data": {"distance": distance, "status": 4}
            })
        # 🚀 2. 正常拦截：超过管理员设置的打卡半径，但没到 10km，转入申诉 (status: 2)
        elif distance > allowable_radius:
            return jsonify({
                "code": 403,
                "msg": f"定位校验失败：您当前距离【{target_spot}】约 {int(distance)} 米，超出了允许的 {int(allowable_radius)} 米打卡范围！已为您转入人工审核。",
                "data": {"distance": distance, "status": 2}
            })

        # ---------------- 【第二重校验：AI 图像识别校验】 ----------------
        print(f"🖼️ 定位校验通过！正在启动 AI 图像识别...")

        # 读取并处理图片
        img = Image.open(io.BytesIO(file.read())).convert('RGB')
        img_tensor = transform(img).unsqueeze(0).to(device)

        # 模型推理
        with torch.no_grad():
            outputs = model(img_tensor)
            # 获取最高置信度的类别索引
            _, predicted = torch.max(outputs, 1)
            predicted_idx = predicted.item()
            predicted_spot = CLASS_NAMES[predicted_idx]

        # 比对 AI 识别结果与用户宣称的景点
        if predicted_spot == target_spot:
            print(f"🎉 打卡成功！AI 识别结果: {predicted_spot}")
            return jsonify({
                "code": 200,
                "msg": f"打卡成功！AI 确认您在【{target_spot}】。",
                "data": {"ai_predict": predicted_spot, "distance": distance, "status": 1}
            })
        else:
            print(f"⚠️ 打卡失败！目标: {target_spot}, AI 识别为: {predicted_spot}")
            return jsonify({
                "code": 403,
                "msg": f"AI 识别失败：照片内容看起来像【{predicted_spot}】，而不像【{target_spot}】的标志性景观。请调整角度重新拍摄或提起申诉。",
                "data": {"ai_predict": predicted_spot, "distance": distance, "status": 2}
            })

    except Exception as e:
        print(f"❌ 服务器内部错误: {str(e)}")
        return jsonify({"code": 500, "msg": f"服务器内部错误: {str(e)}"})


if __name__ == '__main__':
    # 启动 Flask 服务，运行在 5000 端口，host='0.0.0.0' 允许局域网内其他设备访问
    app.run(host='0.0.0.0', port=5000, debug=True)
