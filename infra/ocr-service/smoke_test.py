import argparse
import json
import os
import sys
import uuid
from io import BytesIO
from urllib import request

from PIL import Image, ImageDraw, ImageFont


def choose_font(size: int) -> ImageFont.ImageFont:
    candidates = [
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "C:/Windows/Fonts/msyh.ttc",
        "C:/Windows/Fonts/simhei.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def build_sample_image() -> bytes:
    image = Image.new("RGB", (960, 540), "white")
    draw = ImageDraw.Draw(image)
    font = choose_font(30)
    text = (
        "物联网环境监测系统\n"
        "├─ 数据采集层\n"
        "│  ├─ 串口通信模块 (Serial.cpp)\n"
        "│  └─ 传感器数据解析模块 (SensorModule.cpp)\n"
        "├─ 数据处理层\n"
        "│  ├─ 数据存储模块 (StorageModule.cpp)\n"
        "│  └─ 报警检测模块 (AlarmModule.cpp)"
    )
    draw.multiline_text((36, 28), text, fill="black", font=font, spacing=10)
    output = BytesIO()
    image.save(output, format="PNG")
    return output.getvalue()


def post_ocr(base_url: str, image_bytes: bytes) -> dict:
    boundary = "KnowflowSmoke" + uuid.uuid4().hex
    head = (
        f"--{boundary}\r\n"
        "Content-Disposition: form-data; name=\"file\"; filename=\"smoke.png\"\r\n"
        "Content-Type: image/png\r\n\r\n"
    ).encode()
    tail = f"\r\n--{boundary}--\r\n".encode()
    req = request.Request(
        base_url.rstrip("/") + "/ocr",
        data=head + image_bytes + tail,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST",
    )
    with request.urlopen(req, timeout=90) as response:
        return json.loads(response.read().decode("utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser(description="Smoke test Knowflow OCR service with a generated text image.")
    parser.add_argument("--url", default=os.getenv("OCR_BASE_URL", "http://localhost:8000"))
    args = parser.parse_args()

    payload = post_ocr(args.url, build_sample_image())
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    text = str(payload.get("text", "")).strip()
    expected_tokens = ["物联网", "数据", "Serial", "Sensor", "Alarm"]
    if not payload.get("success") or not text:
        print("OCR smoke test failed: empty or unsuccessful OCR result", file=sys.stderr)
        return 1
    if not any(token in text for token in expected_tokens):
        print("OCR smoke test failed: result does not contain expected sample tokens", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
