import torch
import torch.nn as nn
import torch.optim as optim
from torchvision import datasets, transforms, models
import os

# ================= 配置参数 =================
TRAIN_DIR = './dataset/train'  # 你的训练集路径
WEIGHTS_PATH = './mobilenet_v3_small_local.pth'  # 本地预训练权重路径
SAVE_MODEL_PATH = './my_scenic_spot_model.pth'  # 训练完后保存的模型名字
BATCH_SIZE = 16
NUM_EPOCHS = 50
LEARNING_RATE = 0.001


# ============================================

def main():

    device = torch.device(
        "cuda" if torch.cuda.is_available() else "mps" if torch.backends.mps.is_available() else "cpu")
    print(f"🖥️ 当前使用的计算设备: {device}")


    transform = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.RandomHorizontalFlip(),  # 数据增强：随机水平翻转防过拟合
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
    ])


    if not os.path.exists(TRAIN_DIR):
        raise FileNotFoundError(f"找不到训练目录 '{TRAIN_DIR}'，请按要求建好文件夹！")

    train_dataset = datasets.ImageFolder(root=TRAIN_DIR, transform=transform)
    train_loader = torch.utils.data.DataLoader(train_dataset, batch_size=BATCH_SIZE, shuffle=True)

    # 核心步骤：自动获取景区类别名称和数量！
    class_names = train_dataset.classes
    num_classes = len(class_names)
    print(f"📂 扫描到 {num_classes} 个景区类别: {class_names}")
    print(f"🖼️ 总共加载了 {len(train_dataset)} 张训练图片。")

    # 4. 构建模型并加载本地预训练权重
    print("⚙️ 正在构建 MobileNet V3-Small 模型...")
    # 创建空模型 (不下载默认权重)
    model = models.mobilenet_v3_small(weights=None)

    # 加载本地的 .pth 权重文件
    if os.path.exists(WEIGHTS_PATH):
        model.load_state_dict(torch.load(WEIGHTS_PATH))
        print("✅ 成功加载本地预训练权重！")
    else:
        print("⚠️ 未找到本地权重文件，将从头开始训练(不推荐)！")

    # 5. 自适应修改分类头 (Transfer Learning)
    # MobileNet V3 的分类器最后是一个 Linear 层。获取它的输入维度，然后把输出维度改成你的景区数量
    in_features = model.classifier[3].in_features
    model.classifier[3] = nn.Linear(in_features, num_classes)
    print(f"🔄 模型最后一层已修改为 {num_classes} 分类。")

    # 将模型放入 GPU/CPU
    model = model.to(device)

    # 6. 定义损失函数和优化器
    criterion = nn.CrossEntropyLoss()
    optimizer = optim.Adam(model.parameters(), lr=LEARNING_RATE)

    # 7. 开始训练循环
    print("🚀 开始训练...")
    for epoch in range(NUM_EPOCHS):
        model.train()  # 设置为训练模式
        running_loss = 0.0
        correct = 0
        total = 0

        for i, (inputs, labels) in enumerate(train_loader):
            inputs, labels = inputs.to(device), labels.to(device)

            # 梯度清零
            optimizer.zero_grad()

            # 前向传播 -> 计算损失 -> 反向传播 -> 更新权重
            outputs = model(inputs)
            loss = criterion(outputs, labels)
            loss.backward()
            optimizer.step()

            # 统计数据
            running_loss += loss.item()
            _, predicted = torch.max(outputs.data, 1)
            total += labels.size(0)
            correct += (predicted == labels).sum().item()

        epoch_loss = running_loss / len(train_loader)
        epoch_acc = 100 * correct / total
        print(f"Epoch [{epoch + 1}/{NUM_EPOCHS}] -> Loss: {epoch_loss:.4f}, Accuracy: {epoch_acc:.2f}%")

    # 8. 保存你训练好的专属模型
    torch.save(model.state_dict(), SAVE_MODEL_PATH)
    print(f"🎉 训练完成！专属模型已保存至: {SAVE_MODEL_PATH}")



if __name__ == "__main__":
    main()
