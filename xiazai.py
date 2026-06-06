import torch
import torchvision.models as models

print("⏳ 正在通过 PyTorch 内部通道安全下载 MobileNet V3-Small 权重...")

try:
    # 1. 自动拉取官方最新预训练权重 (底层会自动处理下载和缓存)
    # 这一步如果卡住，请耐心等待一两分钟
    model = models.mobilenet_v3_small(weights=models.MobileNet_V3_Small_Weights.DEFAULT)

    # 2. 把下载好的干净权重，提取并保存到你当前目录
    save_path = "./mobilenet_v3_small_local.pth"
    torch.save(model.state_dict(), save_path)

    print(f"✅ 下载并保存成功！文件已生成: {save_path}")
    print("💡 现在你可以把这个文件用于训练脚本了！")

except Exception as e:
    print(f"❌ 下载失败，可能是网络问题: {e}")