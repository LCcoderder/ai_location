import argparse
import concurrent.futures
import time

import requests


def post_checkin(index, args):
    started = time.time()
    with open(args.image, "rb") as image_file:
        files = {
            "image": (args.image.split("\\")[-1].split("/")[-1], image_file, "image/jpeg"),
        }
        data = {
            "userId": str(args.user_id),
            "spotId": str(args.spot_id),
            "latitude": str(args.latitude),
            "longitude": str(args.longitude),
        }
        response = requests.post(args.url, files=files, data=data, timeout=args.timeout)
    elapsed = time.time() - started
    return index, response.status_code, elapsed, response.text[:300]


def main():
    parser = argparse.ArgumentParser(description="Concurrent check-in duplicate-submit test.")
    parser.add_argument("--url", default="http://127.0.0.1:8080/api/checkin/doCheckin")
    parser.add_argument("--user-id", type=int, required=True)
    parser.add_argument("--spot-id", type=int, required=True)
    parser.add_argument("--latitude", type=float, required=True)
    parser.add_argument("--longitude", type=float, required=True)
    parser.add_argument("--image", required=True)
    parser.add_argument("--requests", type=int, default=2)
    parser.add_argument("--concurrency", type=int, default=2)
    parser.add_argument("--timeout", type=int, default=60)
    args = parser.parse_args()

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        futures = [executor.submit(post_checkin, i + 1, args) for i in range(args.requests)]
        for future in concurrent.futures.as_completed(futures):
            index, status_code, elapsed, body = future.result()
            print(f"#{index} http={status_code} elapsed={elapsed:.2f}s body={body}")


if __name__ == "__main__":
    main()
