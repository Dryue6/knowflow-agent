from io import BytesIO
import logging
import time
from typing import Any

import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from PIL import Image
from pydantic import BaseModel


class OcrResponse(BaseModel):
    success: bool
    text: str = ""
    confidence: float = 0.0
    elapsedMs: int = 0
    error: str | None = None


app = FastAPI(title="Knowflow OCR Service")
_engine: Any | None = None
logger = logging.getLogger("knowflow-ocr")


def get_engine() -> Any:
    global _engine
    if _engine is not None:
        return _engine
    try:
        from rapidocr_onnxruntime import RapidOCR
    except ImportError:
        from rapidocr import RapidOCR
    _engine = RapidOCR()
    return _engine


def parse_result(raw_result: Any) -> tuple[str, float]:
    if not raw_result:
        return "", 0.0
    if hasattr(raw_result, "txts") and hasattr(raw_result, "scores"):
        texts = [str(text).strip() for text in raw_result.txts if str(text).strip()]
        scores = [float(score or 0.0) for score in raw_result.scores]
        confidence = sum(scores) / len(scores) if scores else 0.0
        return "\n".join(texts), confidence
    if isinstance(raw_result, tuple) and len(raw_result) >= 1:
        raw_result = raw_result[0]
    lines: list[str] = []
    scores: list[float] = []
    for item in raw_result or []:
        text = ""
        score = 0.0
        if isinstance(item, dict):
            text = str(item.get("text") or item.get("rec_text") or "")
            score = float(item.get("score") or item.get("confidence") or item.get("rec_score") or 0.0)
        elif isinstance(item, (list, tuple)) and len(item) >= 3:
            text = str(item[1] or "")
            score = float(item[2] or 0.0)
        if text.strip():
            lines.append(text.strip())
            scores.append(score)
    confidence = sum(scores) / len(scores) if scores else 0.0
    return "\n".join(lines), confidence


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/ready")
def ready() -> dict[str, str]:
    try:
        get_engine()
        return {"status": "ready"}
    except Exception as exc:
        logger.exception("OCR engine readiness check failed")
        raise HTTPException(status_code=503, detail=f"OCR engine unavailable: {exc}") from exc


@app.post("/ocr", response_model=OcrResponse)
async def ocr(file: UploadFile = File(...)) -> OcrResponse:
    start = time.time()
    try:
        image_bytes = await file.read()
        image = Image.open(BytesIO(image_bytes)).convert("RGB")
        result = get_engine()(np.array(image))
        text, confidence = parse_result(result)
        return OcrResponse(
            success=True,
            text=text,
            confidence=confidence,
            elapsedMs=int((time.time() - start) * 1000),
        )
    except Exception as exc:
        logger.exception("OCR request failed")
        return OcrResponse(
            success=False,
            error=str(exc),
            elapsedMs=int((time.time() - start) * 1000),
        )
