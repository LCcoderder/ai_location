import os
import shutil
import requests
import time # 新增：用于增加延时，防止高德API并发拦截
# ================= 配置区 =================
# 1. 你的数据集路径
DATASET_PATH = r"C:\Users\Administrator\Desktop\ai定位打卡\ai定位\dataset\train"

# 2. 你 Spring Boot 项目里存图片的真实绝对路径 (请替换成你的真实路径，注意最后要带斜杠或双斜杠)
# 例如: r"D:\IdeaProjects\ai_springboot\uploads"
SPRINGBOOT_UPLOADS_DIR = r"C:\Users\Administrator\Desktop\ai定位打卡\ai定位\ai_springboot\uploads"

# 3. 刚才申请的高德 Web 服务 Key
AMAP_KEY = "e908ae8fd5b9637fc9f830687684055d"
# 放宽到整个陕西省
CITY = "陕西"


# ==========================================

def get_location_from_amap(address):
    """调用高德 API 获取经纬度"""
    # 优化：在景点名前面加上“陕西省”，提高高德地理编码的识别命中率
    search_address = f"陕西省{address}"
    url = f"https://restapi.amap.com/v3/geocode/geo?address={search_address}&city={CITY}&key={AMAP_KEY}"

    try:
        response = requests.get(url).json()
        if response['status'] == '1' and len(response['geocodes']) > 0:
            location = response['geocodes'][0]['location']
            # 高德返回的是 经度,纬度
            lon, lat = location.split(',')
            return lon, lat
    except Exception as e:
        print(f"获取 {address} 坐标失败: {e}")
    return "0.000000", "0.000000"


def main():
    if not os.path.exists(SPRINGBOOT_UPLOADS_DIR):
        os.makedirs(SPRINGBOOT_UPLOADS_DIR)

    sql_statements = []

    # 遍历数据集目录
    print("开始扫描数据集并请求高德 API...\n")
    for spot_name in os.listdir(DATASET_PATH):
        spot_dir = os.path.join(DATASET_PATH, spot_name)

        # 确保是文件夹
        if not os.path.isdir(spot_dir):
            continue

        # 1. 获取文件夹里的第一张图片
        valid_extensions = ('.jpg', '.jpeg', '.png', '.webp')
        images = [f for f in os.listdir(spot_dir) if f.lower().endswith(valid_extensions)]
        if not images:
            print(f"⚠️ 警告: 文件夹 '{spot_name}' 内没有找到图片，已跳过。")
            continue

        first_image = images[0]
        src_image_path = os.path.join(spot_dir, first_image)

        # 2. 拷贝图片到 Spring Boot 目录
        ext = os.path.splitext(first_image)[1]
        new_image_name = f"{spot_name}{ext}"
        dest_image_path = os.path.join(SPRINGBOOT_UPLOADS_DIR, new_image_name)

        shutil.copy(src_image_path, dest_image_path)

        # 这个路径是存进数据库给前端访问的网络路径
        db_image_url = f"/uploads/{new_image_name}"

        # 3. 请求高德 API 获取 GPS
        lon, lat = get_location_from_amap(spot_name)

        # 4. 生成 SQL 语句
        desc = f"欢迎来到{spot_name}！这里是著名的文化旅游胜地，快来打卡记录你的足迹吧。"
        sql = f"INSERT INTO scenic_spot (name, ai_label, latitude, longitude, description, reward_points, base_heat, image_url, create_time) VALUES ('{spot_name}', '{spot_name}', {lat}, {lon}, '{desc}', 10, 100, '{db_image_url}', NOW());"

        sql_statements.append(sql)
        print(f"✅ 成功构建: {spot_name} (经度:{lon}, 纬度:{lat})")

        # ⚠️ 极其重要：每次请求完停顿 0.2 秒，防止触发免费版 API 的并发拦截
        time.sleep(0.2)

    # 输出所有 SQL
    print("\n\n================ 请复制以下 SQL 到数据库执行 ================\n")
    for sql in sql_statements:
        print(sql)
    print("\n===========================================================\n")


if __name__ == '__main__':
    main()