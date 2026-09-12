"""Classification endpoints: the core inference path.

Flow: auth -> validate -> preprocess (+hash) -> cache lookup -> inference on miss ->
cache store -> (best-effort) prediction log + metrics.
"""

from __future__ import annotations

import time

import numpy as np
from fastapi import APIRouter, Depends, File, HTTPException, UploadFile, status
from sqlalchemy.orm import Session

from app.api.deps import get_cache_service, get_inference_service, settings_dep
from app.core.config import Settings
from app.core.logging import get_logger
from app.core.metrics import CACHE_EVENTS, CLASSIFY_LATENCY
from app.core.security import authorize
from app.db.session import get_db
from app.models.schemas import BatchClassifyResponse, ClassifyResponse, LabelPrediction
from app.services import preprocessing
from app.services.cache import CacheService
from app.services.inference import InferenceService
from app.services.prediction_log import record_prediction
from app.services.preprocessing import UnsupportedImageError

logger = get_logger(__name__)
router = APIRouter(
    prefix="/classify",
    tags=["classification"],
    dependencies=[Depends(authorize("classify"))],
)

_ALLOWED_CONTENT_TYPES = {"image/jpeg", "image/png", "image/webp"}


def _validate(file: UploadFile, raw: bytes, settings: Settings) -> None:
    if file.content_type not in _ALLOWED_CONTENT_TYPES:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail=f"Unsupported content type: {file.content_type}",
        )
    if len(raw) == 0:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Empty upload")
    if len(raw) > settings.max_upload_bytes:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail="Image exceeds maximum allowed size",
        )


def _decode(raw: bytes) -> tuple[np.ndarray, str]:
    try:
        image = preprocessing.decode_image(raw)
        tensor = preprocessing.preprocess(image)
    except UnsupportedImageError as exc:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE, detail=str(exc)
        ) from exc
    return tensor, preprocessing.content_hash(tensor)


def _finalize(
    db: Session,
    image_hash: str,
    predictions: list[LabelPrediction],
    inference: InferenceService,
    cached: bool,
    start: float,
) -> ClassifyResponse:
    latency = (time.perf_counter() - start) * 1000
    result = "hit" if cached else "miss"
    CACHE_EVENTS.labels(result=result).inc()
    CLASSIFY_LATENCY.labels(cache=result).observe(latency / 1000.0)
    logger.info("classify", extra={"extra": {"cache": result, "latency_ms": latency}})
    if not cached:
        record_prediction(db, image_hash, inference.model_version, predictions)
    return ClassifyResponse(
        labels=predictions,
        model_version=inference.model_version,
        cached=cached,
        latency_ms=latency,
    )


@router.post("", response_model=ClassifyResponse)
async def classify(
    file: UploadFile = File(...),
    settings: Settings = Depends(settings_dep),
    inference: InferenceService = Depends(get_inference_service),
    cache: CacheService = Depends(get_cache_service),
    db: Session = Depends(get_db),
) -> ClassifyResponse:
    """Classify a single product image into multi-label categories."""
    start = time.perf_counter()
    raw = await file.read()
    _validate(file, raw, settings)
    tensor, image_hash = _decode(raw)

    cached = cache.get(image_hash)
    if cached is not None:
        return _finalize(db, image_hash, cached, inference, True, start)

    predictions = inference.predict(tensor)
    cache.set(image_hash, predictions)
    return _finalize(db, image_hash, predictions, inference, False, start)


@router.post("/batch", response_model=BatchClassifyResponse)
async def classify_batch(
    files: list[UploadFile] = File(...),
    settings: Settings = Depends(settings_dep),
    inference: InferenceService = Depends(get_inference_service),
    cache: CacheService = Depends(get_cache_service),
    db: Session = Depends(get_db),
) -> BatchClassifyResponse:
    """Classify several images. Cache hits are served directly; misses are batched into
    a single ONNX forward pass (dynamic batch axis) for throughput."""
    if not files:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="No files")

    decoded: list[tuple[np.ndarray, str]] = []
    for file in files:
        raw = await file.read()
        _validate(file, raw, settings)
        decoded.append(_decode(raw))

    results: list[ClassifyResponse | None] = [None] * len(decoded)
    miss_indices: list[int] = []
    miss_tensors: list[np.ndarray] = []

    for i, (tensor, image_hash) in enumerate(decoded):
        start = time.perf_counter()
        cached = cache.get(image_hash)
        if cached is not None:
            results[i] = _finalize(db, image_hash, cached, inference, True, start)
        else:
            miss_indices.append(i)
            miss_tensors.append(tensor)

    if miss_tensors:
        batch = np.concatenate(miss_tensors, axis=0)
        batch_preds = inference.predict_batch(batch)
        for j, i in enumerate(miss_indices):
            start = time.perf_counter()
            _, image_hash = decoded[i]
            preds = batch_preds[j]
            cache.set(image_hash, preds)
            results[i] = _finalize(db, image_hash, preds, inference, False, start)

    return BatchClassifyResponse(results=[r for r in results if r is not None])
