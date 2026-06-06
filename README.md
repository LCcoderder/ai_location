# Concurrency Test Scripts

Install the Python dependency once:

```powershell
pip install requests
```

Check-in duplicate-submit test:

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

Expected result: only one request should enter the real check-in flow. Other requests should return `429` or `409`.

Mall exchange concurrent test:

```powershell
python scripts/concurrency/mall_exchange_test.py `
  --user-id 1 `
  --item-id 1 `
  --requests 5 `
  --concurrency 5
```

Expected result: one request succeeds or enters the stock/points flow. Other duplicate requests from the same user should return `429` inside the duplicate window.

Ramp QPS load test:

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

`--user-id-span` rotates user IDs. For check-in load tests, use a larger span to avoid the normal idempotency rule turning every request after the first one into `409`.

Mall ramp test:

```powershell
python scripts/concurrency/ramp_qps_load_test.py `
  --endpoint mall `
  --user-id 1 `
  --item-id 1 `
  --qps-levels 1,2,5,10 `
  --stage-seconds 20 `
  --max-workers 100
```

The script prints target QPS, real QPS, request count, average latency, P50, P95, P99, HTTP status counts, and business `code` counts.

Kafka note:

Current check-in recognition is Kafka async by default. Spring Boot creates a `checkin_record` with `status=0`, sends a `CHECKIN_VERIFY_REQUESTED` task to Kafka, and returns quickly. Python AI workers consume the task and call back to update the final result.

Make sure the worker is running before check-in pressure tests:

```powershell
cd ai定位
python kafka_ai_worker.py
```

This design smooths traffic spikes and avoids tying user HTTP requests directly to slow model inference.
