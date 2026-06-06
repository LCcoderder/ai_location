import argparse
import concurrent.futures
import time

import requests


def post_exchange(index, args):
    started = time.time()
    data = {
        "userId": str(args.user_id),
        "itemId": str(args.item_id),
        "shippingInfo": args.shipping_info,
    }
    response = requests.post(args.url, data=data, timeout=args.timeout)
    elapsed = time.time() - started
    return index, response.status_code, elapsed, response.text[:300]


def main():
    parser = argparse.ArgumentParser(description="Concurrent mall exchange test.")
    parser.add_argument("--url", default="http://127.0.0.1:8080/api/mall/exchange")
    parser.add_argument("--user-id", type=int, required=True)
    parser.add_argument("--item-id", type=int, required=True)
    parser.add_argument("--shipping-info", default="concurrency-test")
    parser.add_argument("--requests", type=int, default=5)
    parser.add_argument("--concurrency", type=int, default=5)
    parser.add_argument("--timeout", type=int, default=30)
    args = parser.parse_args()

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        futures = [executor.submit(post_exchange, i + 1, args) for i in range(args.requests)]
        for future in concurrent.futures.as_completed(futures):
            index, status_code, elapsed, body = future.result()
            print(f"#{index} http={status_code} elapsed={elapsed:.2f}s body={body}")


if __name__ == "__main__":
    main()
