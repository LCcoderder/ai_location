import io
import json
import os
import sys
import time

import requests
import torch
from kafka import KafkaConsumer
from PIL import Image

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
os.chdir(SCRIPT_DIR)
sys.path.insert(0, SCRIPT_DIR)

from app import CLASS_NAMES, calculate_distance, device, get_spot_location_amap, model, transform  # noqa: E402


KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:9092")
KAFKA_TOPIC = os.getenv("AI_CHECKIN_REQUEST_TOPIC", "ai-tour-ai-checkin-requests")
KAFKA_GROUP_ID = os.getenv("AI_WORKER_GROUP_ID", "ai-tour-ai-workers")
BACKEND_BASE_URL = os.getenv("AI_TOUR_BACKEND_PUBLIC_URL", "http://127.0.0.1:8080").rstrip("/")
CALLBACK_TOKEN = os.getenv("AI_TOUR_AI_CALLBACK_TOKEN", "demo-secret")
WORKER_ID = os.getenv("AI_WORKER_ID", str(os.getpid()))


def verify_checkin(payload):
    target_spot = payload["targetSpot"]
    user_lat = float(payload["latitude"])
    user_lon = float(payload["longitude"])
    allowable_radius = float(payload.get("allowableRadius", 1000))

    target_lat, target_lon = get_spot_location_amap(target_spot)
    if not target_lat:
        return {
            "code": 500,
            "status": 2,
            "msg": f"系统错误：未能通过高德地图找到【{target_spot}】的真实坐标",
        }

    distance = calculate_distance(user_lat, user_lon, target_lat, target_lon)
    if distance > 10000:
        return {
            "code": 403,
            "status": 4,
            "distance": distance,
            "msg": f"定位校验失败：当前距离【{target_spot}】约 {int(distance)} 米，距离过远，无法申诉。",
        }
    if distance > allowable_radius:
        return {
            "code": 403,
            "status": 2,
            "distance": distance,
            "msg": f"定位校验失败：当前距离【{target_spot}】约 {int(distance)} 米，已转入人工审核。",
        }

    image_bytes = download_image(payload)
    img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    img_tensor = transform(img).unsqueeze(0).to(device)

    with torch.no_grad():
        outputs = model(img_tensor)
        _, predicted = torch.max(outputs, 1)
        predicted_spot = CLASS_NAMES[predicted.item()]

    if predicted_spot == target_spot:
        return {
            "code": 200,
            "status": 1,
            "aiPredict": predicted_spot,
            "distance": distance,
            "msg": f"打卡成功！AI 确认您在【{target_spot}】。",
        }
    return {
        "code": 403,
        "status": 2,
        "aiPredict": predicted_spot,
        "distance": distance,
        "msg": f"AI 识别失败：照片内容像【{predicted_spot}】，不像【{target_spot}】，已转入人工审核。",
    }


def download_image(payload):
    image_url = payload.get("imageDownloadUrl")
    if not image_url:
        image_url = BACKEND_BASE_URL + payload["imageUrl"]
    response = requests.get(image_url, timeout=30)
    response.raise_for_status()
    return response.content


def callback_backend(payload, result):
    callback_url = payload.get("callbackUrl") or f"{BACKEND_BASE_URL}/api/checkin/ai/callback"
    headers = {"Content-Type": "application/json"}
    if CALLBACK_TOKEN:
        headers["X-AI-CALLBACK-TOKEN"] = CALLBACK_TOKEN
    body = {
        "recordId": payload["recordId"],
        "code": result.get("code"),
        "status": result.get("status"),
        "msg": result.get("msg"),
        "aiPredict": result.get("aiPredict"),
        "distance": result.get("distance"),
    }
    response = requests.post(callback_url, headers=headers, json=body, timeout=30)
    response.raise_for_status()
    response_text = response.text
    try:
        response_body = response.json()
        if response_body.get("code") != 200:
            raise RuntimeError(f"backend callback rejected: {response_text}")
    except ValueError:
        pass
    return response_text


def main():
    consumer = KafkaConsumer(
        KAFKA_TOPIC,
        bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
        group_id=KAFKA_GROUP_ID,
        enable_auto_commit=False,
        auto_offset_reset="earliest",
        value_deserializer=lambda raw: json.loads(raw.decode("utf-8")),
        key_deserializer=lambda raw: raw.decode("utf-8") if raw else None,
        max_poll_records=1,
    )
    print(
        f"AI worker {WORKER_ID} started. topic={KAFKA_TOPIC}, "
        f"group={KAFKA_GROUP_ID}, kafka={KAFKA_BOOTSTRAP_SERVERS}"
    )

    for message in consumer:
        payload = message.value
        record_id = payload.get("recordId")
        started = time.time()
        try:
            result = verify_checkin(payload)
            callback_text = callback_backend(payload, result)
            consumer.commit()
            elapsed = time.time() - started
            print(
                f"worker={WORKER_ID} record={record_id} status={result.get('status')} "
                f"elapsed={elapsed:.2f}s callback={callback_text[:120]}"
            )
        except Exception as exc:
            print(f"worker={WORKER_ID} record={record_id} failed: {exc}")


if __name__ == "__main__":
    main()
