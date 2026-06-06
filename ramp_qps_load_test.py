import argparse
import concurrent.futures
import json
import math
import time
from collections import Counter

import requests


def percentile(values, percent):
    if not values:
        return 0.0
    ordered = sorted(values)
    index = math.ceil((percent / 100.0) * len(ordered)) - 1
    return ordered[max(0, min(index, len(ordered) - 1))]


def choose_user_id(base_user_id, user_id_span, index):
    if user_id_span <= 1:
        return base_user_id
    return base_user_id + (index % user_id_span)


def post_mall(index, args):
    started = time.perf_counter()
    user_id = choose_user_id(args.user_id, args.user_id_span, index)
    try:
        response = requests.post(
            args.mall_url,
            data={
                "userId": str(user_id),
                "itemId": str(args.item_id),
                "shippingInfo": args.shipping_info,
            },
            timeout=args.timeout,
        )
        elapsed = time.perf_counter() - started
        return build_result(response, elapsed)
    except Exception as exc:
        elapsed = time.perf_counter() - started
        return {
            "http_status": "EXCEPTION",
            "business_code": "EXCEPTION",
            "latency": elapsed,
            "body": str(exc),
        }


def post_checkin(index, args):
    started = time.perf_counter()
    user_id = choose_user_id(args.user_id, args.user_id_span, index)
    try:
        with open(args.image, "rb") as image_file:
            response = requests.post(
                args.checkin_url,
                files={
                    "image": (
                        args.image.split("\\")[-1].split("/")[-1],
                        image_file,
                        "image/jpeg",
                    ),
                },
                data={
                    "userId": str(user_id),
                    "spotId": str(args.spot_id),
                    "latitude": str(args.latitude),
                    "longitude": str(args.longitude),
                },
                timeout=args.timeout,
            )
        elapsed = time.perf_counter() - started
        return build_result(response, elapsed)
    except Exception as exc:
        elapsed = time.perf_counter() - started
        return {
            "http_status": "EXCEPTION",
            "business_code": "EXCEPTION",
            "latency": elapsed,
            "body": str(exc),
        }


def build_result(response, elapsed):
    business_code = "NO_JSON"
    try:
        payload = response.json()
        business_code = str(payload.get("code", "NO_CODE"))
    except json.JSONDecodeError:
        payload = response.text[:300]
    return {
        "http_status": str(response.status_code),
        "business_code": business_code,
        "latency": elapsed,
        "body": payload,
    }


def build_qps_levels(args):
    if args.qps_levels:
        return [float(item.strip()) for item in args.qps_levels.split(",") if item.strip()]
    levels = []
    current = args.start_qps
    while current <= args.end_qps + 1e-9:
        levels.append(float(current))
        current += args.step_qps
    return levels


def run_stage(stage_no, qps, args, index_offset):
    total_requests = max(1, int(qps * args.stage_seconds))
    interval = 1.0 / qps
    latencies = []
    http_counter = Counter()
    business_counter = Counter()
    started = time.perf_counter()
    worker = post_checkin if args.endpoint == "checkin" else post_mall

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.max_workers) as executor:
        futures = []
        for index in range(total_requests):
            global_index = index_offset + index
            due_time = started + index * interval
            sleep_seconds = due_time - time.perf_counter()
            if sleep_seconds > 0:
                time.sleep(sleep_seconds)
            futures.append(executor.submit(worker, global_index, args))

        for future in concurrent.futures.as_completed(futures):
            result = future.result()
            latencies.append(result["latency"])
            http_counter[result["http_status"]] += 1
            business_counter[result["business_code"]] += 1

    elapsed = time.perf_counter() - started
    real_qps = total_requests / elapsed if elapsed > 0 else 0
    print(
        f"stage={stage_no} target_qps={qps:.2f} real_qps={real_qps:.2f} "
        f"requests={total_requests} elapsed={elapsed:.2f}s"
    )
    print(
        f"  latency: avg={sum(latencies) / len(latencies):.3f}s "
        f"p50={percentile(latencies, 50):.3f}s "
        f"p95={percentile(latencies, 95):.3f}s "
        f"p99={percentile(latencies, 99):.3f}s "
        f"max={max(latencies):.3f}s"
    )
    print(f"  http_status: {dict(http_counter)}")
    print(f"  business_code: {dict(business_counter)}")


def main():
    parser = argparse.ArgumentParser(description="Ramp QPS load test for ai-tour backend.")
    parser.add_argument("--endpoint", choices=["checkin", "mall"], required=True)
    parser.add_argument("--start-qps", type=float, default=1)
    parser.add_argument("--end-qps", type=float, default=10)
    parser.add_argument("--step-qps", type=float, default=1)
    parser.add_argument("--qps-levels", help="Comma-separated QPS levels, for example: 1,2,5,10")
    parser.add_argument("--stage-seconds", type=int, default=20)
    parser.add_argument("--max-workers", type=int, default=100)
    parser.add_argument("--timeout", type=int, default=90)

    parser.add_argument("--user-id", type=int, required=True)
    parser.add_argument(
        "--user-id-span",
        type=int,
        default=1,
        help="Rotate user IDs from user-id to user-id + span - 1. Use a larger span to avoid idempotency blocking all check-in load.",
    )

    parser.add_argument("--checkin-url", default="http://127.0.0.1:8080/api/checkin/doCheckin")
    parser.add_argument("--spot-id", type=int)
    parser.add_argument("--latitude", type=float)
    parser.add_argument("--longitude", type=float)
    parser.add_argument("--image")

    parser.add_argument("--mall-url", default="http://127.0.0.1:8080/api/mall/exchange")
    parser.add_argument("--item-id", type=int)
    parser.add_argument("--shipping-info", default="ramp-qps-test")
    args = parser.parse_args()

    if args.endpoint == "checkin":
        missing = [
            name
            for name in ["spot_id", "latitude", "longitude", "image"]
            if getattr(args, name) is None
        ]
        if missing:
            parser.error("checkin endpoint requires: " + ", ".join("--" + item.replace("_", "-") for item in missing))
    if args.endpoint == "mall" and args.item_id is None:
        parser.error("mall endpoint requires: --item-id")

    if args.step_qps <= 0:
        parser.error("--step-qps must be greater than 0")
    if args.stage_seconds <= 0:
        parser.error("--stage-seconds must be greater than 0")

    index_offset = 0
    for stage_no, qps in enumerate(build_qps_levels(args), start=1):
        print("=" * 72)
        run_stage(stage_no, qps, args, index_offset)
        index_offset += max(1, int(qps * args.stage_seconds))


if __name__ == "__main__":
    main()
