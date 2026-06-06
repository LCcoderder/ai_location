import os
from icrawler.builtin import BaiduImageCrawler, BingImageCrawler

# 你的 25 个景点清单
scenic_spots = [
    "西安兵马俑", "西安大雁塔", "西安钟楼", "西安鼓楼", "西安城墙南门",
    "西安小雁塔", "大唐芙蓉园紫云楼", "大唐不夜城", "西安华清宫", "西安大明宫丹凤门",
    "西安曲江池", "陕西历史博物馆", "西安碑林博物馆", "西安半坡博物馆", "汉景帝阳陵",
    "西安青龙寺", "西安广仁寺", "西安回民街高家大院", "关中民俗艺术博物院", "华山风景区",
    "临潼骊山", "西安翠华山", "西安楼观台", "西安世博园长安塔", "西安昆明池"
]

# 图片保存的主目录（与你之前代码里的 TRAIN_DIR 保持一致）
BASE_DIR = './dataset/train'


def crawl_images(keyword, max_num=60):
    """
    根据关键字爬取图片并分类保存
    max_num: 每个景点爬取的数量，建议多爬一点，方便后续人工清洗
    """
    # 自动为每个景点创建独立的文件夹
    save_dir = os.path.join(BASE_DIR, keyword)
    if not os.path.exists(save_dir):
        os.makedirs(save_dir)

    print(f"🚀 正在爬取 [{keyword}] 的图片，保存至 {save_dir}...")

    # 使用必应图片搜索引擎（通常比百度稳定，且高清图多）
    crawler = BingImageCrawler(
        feeder_threads=1,
        parser_threads=1,
        downloader_threads=4,  # 4个线程同时下载
        storage={'root_dir': save_dir}
    )

    # 开始抓取
    crawler.crawl(keyword=keyword, max_num=max_num)
    print(f"✅ [{keyword}] 爬取完成！\n")


if __name__ == '__main__':
    print("⏳ 开始批量构建西安文旅打卡数据集...")
    for spot in scenic_spots:
        crawl_images(spot, max_num=50)  # 每个景点先爬 50 张测试

    print(f"🎉 全部下载完毕！请前往 {BASE_DIR} 进行人工清洗。")