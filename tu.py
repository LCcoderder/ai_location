import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np
import os

save_dir = r'C:\Users\Administrator\Desktop\ai定位打卡\ai定位\loss_results'

# 把你今晚跑的实验配置写在这里
experiments = [
    {"file": "test_loss_sp0_ep20.npy", "label": "SP Config 0, Local Ep 20", "color": "blue"},
    {"file": "test_loss_sp1_ep20.npy", "label": "SP Config 1, Local Ep 20", "color": "green"},
    {"file": "test_loss_sp1_ep15.npy", "label": "SP Config 1, Local Ep 15", "color": "red"}
]

plt.figure(figsize=(10, 6), dpi=300)

for exp in experiments:
    file_path = os.path.join(save_dir, exp["file"])
    if os.path.exists(file_path):
        data = np.load(file_path)
        x_axis = np.arange(1, len(data) + 1)
        plt.plot(x_axis, data, label=exp["label"], color=exp["color"], linewidth=1.5)
    else:
        print(f"等待数据生成: {file_path}")

plt.title("Convergence Comparison: Test Loss", fontsize=14, fontweight='bold')
plt.xlabel("Communication Rounds", fontsize=12)
plt.ylabel("Testing Loss", fontsize=12)
plt.grid(True, linestyle='--', alpha=0.6)
plt.legend(loc='upper right')

plt.savefig('Loss_Convergence_Comparison.png', bbox_inches='tight')
print("✅ 图表已生成：Loss_Convergence_Comparison.png")