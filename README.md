# AI 定位打卡文旅小程序

基于微信小程序、Spring Boot、MySQL、Redis、Kafka、Flask 和 PyTorch 的文旅景点 AI 防伪打卡系统。项目围绕“用户到达真实景点后拍照打卡”的业务场景，完成了景点展示、定位校验、AI 图像识别、积分奖励、积分商城、社区内容、后台管理、高并发控制、Redis 分布式锁、MySQL 幂等控制、Kafka 异步 AI 任务队列和并发压测脚本。

## 文档入口

| 文档 | 说明 |
| --- | --- |
| [项目详细文档](docs/project-documentation.md) | 完整说明项目功能、技术栈、数据库、接口、Redis、Kafka、AI Worker、测试和上线建议 |
| [并发 Redis Kafka 说明](docs/concurrency-redis-kafka.md) | 专门说明并发控制、Redis 锁、MySQL 幂等、Kafka 事件和 AI 异步队列 |
| [并发压测脚本说明](scripts/concurrency/README.md) | 说明双击打卡、商城兑换、阶梯式 QPS 压测脚本的使用方式 |

## 项目结构

```text
ai-app/                 微信小程序前端
ai_springboot/          Spring Boot 后端服务
ai定位/                 Python AI 识别服务、Kafka AI Worker、训练脚本、模型和数据集
docs/                   项目文档
scripts/concurrency/    并发测试和压测脚本
```

## 核心功能

- 景点浏览：展示景点封面、热度、详情、位置和积分奖励。
- AI 防伪打卡：用户上传现场照片，系统结合 LBS 定位和 AI 图像识别判断是否真实到达景点。
- Kafka 异步 AI 识别：AI 识别耗时较长时，可以把识别任务投递到 Kafka，由一个或多个 Python AI Worker 并行消费。
- 打卡状态查询：异步模式下先返回“AI 识别中”，小程序和后台可以查询最终状态。
- 积分体系：打卡成功自动发放积分，人工申诉通过后补发积分。
- 积分商城：用户使用积分兑换商品，后端保证库存不超卖、积分不透支。
- 社区内容：用户发布图文游记，后台可设置精选内容。
- 后台管理：管理景点、商品、帖子、订单、用户积分、打卡申诉和统计数据。
- 高并发控制：防重复点击、防重复打卡、防重复发积分、防库存超卖、防重复兑换。
- 测试脚本：提供双击模拟、高并发兑换、阶梯式 QPS 压测脚本。

## 技术栈

| 模块 | 技术 | 用途 |
| --- | --- | --- |
| 小程序 | 微信小程序原生 JS/WXML/WXSS | 用户端页面、拍照、定位、上传、个人中心 |
| 后端 | Spring Boot 2.7、MyBatis、Maven | REST API、业务编排、事务控制、静态资源 |
| 数据库 | MySQL 8 | 用户、景点、打卡、积分、商城、订单、社区、后台数据 |
| 并发锁 | Redis + JVM fallback | 分布式锁、短时间重复提交拦截 |
| 幂等控制 | MySQL 唯一索引 | 同一用户同一景点同一天只允许一次打卡 |
| 乐观锁思想 | MySQL 条件更新 | 申诉状态流转、库存扣减、积分扣减 |
| 消息队列 | Kafka | AI 异步任务队列、打卡事件、商城兑换事件 |
| AI 服务 | Flask、PyTorch、TorchVision、MobileNetV3 | 景点图像识别和定位距离校验 |
| 测试 | JUnit、Python requests、ThreadPoolExecutor | 单元测试、双击测试、并发测试、QPS 压测 |

## 架构说明

```text
微信小程序
  |
  | HTTP 上传图片、位置、景点 ID
  v
Spring Boot 后端
  |
  | MySQL 幂等表 + Redis 分布式锁
  v
打卡记录 status=0/1/2/3/4
  |
  | 同步模式：HTTP 调 Flask AI 服务
  | 异步模式：投递 Kafka AI 任务
  v
Python AI 服务 / Python AI Worker
  |
  | AI 图像识别 + LBS 距离校验
  v
Spring Boot 回写结果、发放积分、发布业务事件
```

## 并发控制亮点

项目不是只在前端禁用按钮，而是在后端和数据库都做了兜底。

### 1. Redis 分布式锁

核心类：

```text
ai_springboot/src/main/java/com/example/ai_springboot/concurrent/ConcurrencyGuard.java
```

Redis 开启后使用 `SET NX + TTL` 加锁，释放锁时使用 Lua 脚本校验 token，避免误删其他请求持有的锁。Redis 未开启时，系统会使用 JVM 本地锁兜底，方便本地演示。

主要 key：

| Key | 作用 |
| --- | --- |
| `lock:checkin:user:{userId}:spot:{spotId}` | 同一用户同一景点打卡互斥 |
| `lock:checkin:appeal:{recordId}` | 同一条申诉处理互斥 |
| `lock:checkin:ai-callback:{recordId}` | 同一 AI 回调结果互斥 |
| `lock:mall:item:{itemId}` | 同一商品兑换互斥 |
| `duplicate:mall:user:{userId}:item:{itemId}` | 短时间重复兑换拦截 |

### 2. MySQL 幂等表

新增 `checkin_guard` 表，用唯一索引保证同一用户、同一景点、同一天只能提交一次打卡。

```sql
UNIQUE KEY uk_checkin_guard_user_spot_day(user_id, spot_id, checkin_date)
```

SQL 补丁：

```text
ai_springboot/sql/concurrency_patch.sql
```

### 3. 乐观锁思想

申诉审批使用状态条件更新：

```sql
UPDATE checkin_record
SET status = #{status}
WHERE id = #{id}
AND status = #{expectedStatus}
```

这样管理员连续点击两次“通过”时，只有第一次能把状态从 `2` 改成 `3`，后续请求不会重复发积分。

库存和积分也使用条件更新：

```sql
UPDATE mall_item SET stock = stock - 1 WHERE id = #{id} AND stock > 0;
UPDATE wx_user SET points_balance = points_balance - #{points}
WHERE id = #{id} AND points_balance >= #{points};
```

## Kafka 用在哪里

Kafka 当前有两个作用。

### 1. AI 识别异步任务队列

AI 识别比较慢，适合从主请求链路里拆出来。开启异步模式后，用户提交打卡时：

1. Spring Boot 保存图片。
2. Spring Boot 创建 `status=0` 的打卡记录。
3. Spring Boot 把 AI 识别任务发送到 `ai-tour-ai-checkin-requests`。
4. Python AI Worker 消费任务并执行识别。
5. Worker 回调 `/api/checkin/ai/callback` 写回结果。
6. 后端成功时发积分、增加景点热度，并发布打卡结果事件。

多个 AI Worker 可以使用相同 consumer group 并行消费。为了让多个 Worker 真正同时工作，Kafka topic 分区数建议大于等于 Worker 数量。

### 2. 业务事件发布

| Topic | 内容 |
| --- | --- |
| `ai-tour-checkin-events` | 打卡成功、进入申诉、申诉通过、申诉驳回 |
| `ai-tour-mall-events` | 商城兑换订单创建 |

这些事件后续可以扩展为异步通知、数据统计、风控审计、用户行为分析等。

## 本地启动

### 0. 启动 Redis 和 Kafka

仓库已经提供了和当前后端配置适配的 `docker-compose.yml`。默认情况下：

- Redis 暴露到宿主机 `127.0.0.1:6379`
- Kafka 暴露到宿主机 `127.0.0.1:9092`
- Kafka 容器内部地址为 `kafka:29092`
- 自动创建 `ai-tour-ai-checkin-requests`、`ai-tour-checkin-events`、`ai-tour-mall-events` 三个 topic
- AI 识别任务 topic 默认 3 个分区，方便多个 AI Worker 并行消费

启动基础服务：

```powershell
docker compose up -d redis zookeeper kafka kafka-init
```

如果你的 Docker 版本较旧，也可以使用：

```powershell
docker-compose up -d redis zookeeper kafka kafka-init
```

后端在 Windows/IDE 里运行时，继续使用下面这些地址即可：

```powershell
$env:REDIS_HOST='127.0.0.1'
$env:REDIS_PORT='6379'
$env:KAFKA_BOOTSTRAP_SERVERS='127.0.0.1:9092'
```

如果以后把后端或 AI Worker 也放进 Docker 容器里，则 Kafka 地址应改为：

```powershell
$env:KAFKA_BOOTSTRAP_SERVERS='kafka:29092'
```

### 1. 导入数据库

先导入你提供的数据库文件：

```text
C:\Users\Administrator\Desktop\ai_tour_db.sql
```

再执行并发补丁：

```sql
source ai_springboot/sql/concurrency_patch.sql;
```

或者在 Navicat 中打开 `ai_springboot/sql/concurrency_patch.sql` 执行。

### 2. 启动后端

当前默认配置已经固定为 Redis + Kafka + AI 异步识别路线。启动后端前，请先保证 Docker 中的 Redis 和 Kafka 已经运行，并且 `6379`、`9092` 端口可以访问。

```powershell
cd ai_springboot
$env:JAVA_HOME='E:\jdk'
$env:Path='E:\jdk\bin;' + $env:Path
.\mvnw.cmd spring-boot:run
```

也可以继续使用 `docker` profile，效果和当前默认配置一致：

```powershell
cd ai_springboot
$env:JAVA_HOME='E:\jdk'
$env:Path='E:\jdk\bin;' + $env:Path
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=docker"
```

默认数据库配置可以通过环境变量覆盖：

```powershell
$env:MYSQL_URL='jdbc:mysql://localhost:3306/ai_tour_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:MYSQL_USERNAME='root'
$env:MYSQL_PASSWORD='your_password'
```

### 3. 启动 Kafka AI Worker

当前打卡接口不会直接等待 Flask AI 返回，而是把 AI 识别任务发送到 Kafka。必须启动 Worker 消费任务，否则打卡记录会一直停留在 `AI识别中`。

```powershell
cd ai定位
pip install -r requirements-ai-worker.txt
$env:KAFKA_BOOTSTRAP_SERVERS='127.0.0.1:9092'
$env:AI_WORKER_GROUP_ID='ai-tour-ai-workers'
$env:AI_TOUR_BACKEND_PUBLIC_URL='http://127.0.0.1:8080'
$env:AI_TOUR_AI_CALLBACK_TOKEN='demo-secret'
python kafka_ai_worker.py
```

可以在多个终端重复启动 `python kafka_ai_worker.py`，模拟多个 AI 识别服务端并行消费任务。

### 4. 可选：旧同步 AI 回退

如果临时不想走 Kafka AI 队列，可以启用 `sync-ai` profile 回退到旧的同步 Flask AI 调用：

```powershell
cd ai_springboot
$env:JAVA_HOME='E:\jdk'
$env:Path='E:\jdk\bin;' + $env:Path
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=sync-ai"
```

回退模式下再启动 Flask AI 服务：

```powershell
cd ai定位
python app.py
```

创建多分区 AI 任务 topic 示例：

```powershell
kafka-topics.bat --bootstrap-server 127.0.0.1:9092 `
  --create `
  --topic ai-tour-ai-checkin-requests `
  --partitions 3 `
  --replication-factor 1
```

如果使用仓库里的 `docker-compose.yml`，`kafka-init` 会自动创建这些 topic，上面的手动创建命令可以不用执行。

### 5. 启动小程序

使用微信开发者工具打开：

```text
ai-app/
```

真机预览时，需要把小程序中的 `localhost` 改为电脑局域网 IP。

## 测试与压测

安装 Python 依赖：

```powershell
pip install requests
```

模拟双击打卡：

```powershell
python scripts/concurrency/checkin_double_click_test.py `
  --user-id 5 `
  --spot-id 7 `
  --latitude 34.372332 `
  --longitude 109.215724 `
  --image "ai_springboot/uploads/临潼骊山.jpg" `
  --requests 2 `
  --concurrency 2
```

预期结果：只有一个请求进入真实打卡流程，其他请求返回 `429` 或 `409`。

模拟高并发兑换：

```powershell
python scripts/concurrency/mall_exchange_test.py `
  --user-id 1 `
  --item-id 1 `
  --requests 5 `
  --concurrency 5
```

阶梯式 QPS 压测：

```powershell
python scripts/concurrency/ramp_qps_load_test.py `
  --endpoint checkin `
  --user-id 10000 `
  --user-id-span 1000 `
  --spot-id 7 `
  --latitude 34.372332 `
  --longitude 109.215724 `
  --image "ai_springboot/uploads/临潼骊山.jpg" `
  --qps-levels 1,2,5,10 `
  --stage-seconds 20 `
  --max-workers 100
```

脚本会输出目标 QPS、实际 QPS、平均延迟、P50、P95、P99、HTTP 状态码分布和业务 `code` 分布。

后端测试：

```powershell
cd ai_springboot
$env:JAVA_HOME='E:\jdk'
$env:Path='E:\jdk\bin;' + $env:Path
.\mvnw.cmd test
```

当前已验证结果：

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

## 上线配置建议

生产环境建议开启 Redis、Kafka 和 AI 异步识别：

```powershell
$env:MYSQL_URL='jdbc:mysql://your-mysql-host:3306/ai_tour_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:MYSQL_USERNAME='your-user'
$env:MYSQL_PASSWORD='your-password'
$env:AI_TOUR_REDIS_LOCK_ENABLED='true'
$env:REDIS_HOST='your-redis-host'
$env:REDIS_PORT='6379'
$env:AI_TOUR_KAFKA_ENABLED='true'
$env:AI_TOUR_AI_ASYNC_ENABLED='true'
$env:KAFKA_BOOTSTRAP_SERVERS='your-kafka-host:9092'
$env:AI_TOUR_BACKEND_PUBLIC_URL='https://your-backend-domain'
$env:AI_TOUR_AI_CALLBACK_TOKEN='your-callback-token'
```

上线前建议继续补强：

- 后台管理接口接入 Spring Security、JWT 或 Session 鉴权。
- 微信 AppSecret、高德 Key、数据库密码全部移到环境变量或配置中心。
- Kafka topic 分区数按 AI Worker 数量配置。
- AI Worker 独立部署，支持横向扩容和失败重试。
- 上传图片迁移到对象存储，并增加文件类型、大小和安全校验。
- 配置 Nginx HTTPS 反向代理。
- 增加日志落盘、错误告警、慢接口监控和 Kafka 死信队列。

## 项目亮点

- AI 图像识别 + LBS 定位双重校验，降低虚假打卡风险。
- Kafka 将慢 AI 识别改造成异步任务队列，支持多个 AI Worker 并行处理。
- Redis 分布式锁支持多后端实例部署下的互斥控制。
- MySQL 唯一索引实现打卡幂等，防止同一用户同一景点同一天重复打卡。
- 基于状态条件更新实现乐观锁思想，防止申诉重复审批和重复发积分。
- 条件扣减 SQL 防止库存超卖和积分透支。
- 提供双击、高并发、阶梯式 QPS 压测脚本，能验证并发加固效果。
