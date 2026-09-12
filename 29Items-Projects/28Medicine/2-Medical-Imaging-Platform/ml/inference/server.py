"""FastAPI wrapper exposing the chest X-ray classifier as a service.

The API/worker calls POST /v1/predict with an object key; this service fetches
the DICOM from object storage, decodes pixels, runs the model, and returns
predictions. Run: `uvicorn inference.server:app --port 8001`.
"""

from __future__ import annotations

from io import BytesIO

import numpy as np
import pydicom
from fastapi import FastAPI
from pydantic import BaseModel

from inference.chest_xray_classifier import ChestXrayClassifier
from inference.storage import get_object

app = FastAPI(title="Chest X-ray Inference", version="0.1.0")
_classifier = ChestXrayClassifier()


@app.on_event("startup")
async def _startup() -> None:
    _classifier.load()


class PredictRequest(BaseModel):
    sop_instance_uid: str
    object_key: str
    bucket: str | None = None


class PredictResponse(BaseModel):
    sop_instance_uid: str
    model_name: str
    model_version: str
    predictions: dict[str, float]
    top_label: str
    top_score: float


def _load_pixels(raw: bytes) -> np.ndarray:
    """Decode pixel data; fall back to a blank frame if pixels are absent."""
    ds = pydicom.dcmread(BytesIO(raw))
    try:
        arr = ds.pixel_array.astype("float64")
    except Exception:  # noqa: BLE001 - missing/compressed pixels → neutral input
        return np.zeros((224, 224), dtype="float64")
    # MONOCHROME1 is stored inverted; flip so higher value == brighter.
    if getattr(ds, "PhotometricInterpretation", "") == "MONOCHROME1":
        arr = arr.max() - arr
    return arr


@app.get("/health/live")
async def live() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/v1/predict", response_model=PredictResponse)
async def predict(req: PredictRequest) -> PredictResponse:
    raw = get_object(req.object_key, req.bucket)
    pixels = _load_pixels(raw)
    result = _classifier.predict(pixels)
    return PredictResponse(
        sop_instance_uid=req.sop_instance_uid,
        model_name=result.model_name,
        model_version=result.model_version,
        predictions=result.predictions,
        top_label=result.top_label,
        top_score=result.top_score,
    )
