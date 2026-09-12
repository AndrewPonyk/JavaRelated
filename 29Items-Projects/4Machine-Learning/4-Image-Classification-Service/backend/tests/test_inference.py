"""Unit tests for the inference + preprocessing services."""

from __future__ import annotations

import numpy as np
from app.services import preprocessing
from app.services.preprocessing import IMAGE_SIZE


def test_predict_applies_thresholds(inference_service):
    tensor = np.zeros((1, 3, IMAGE_SIZE, IMAGE_SIZE), dtype=np.float32)
    preds = inference_service.predict(tensor)
    names = [p.name for p in preds]
    assert "electronics" in names
    assert "home" not in names  # below 0.5 threshold


def test_predictions_sorted_descending(inference_service):
    tensor = np.zeros((1, 3, IMAGE_SIZE, IMAGE_SIZE), dtype=np.float32)
    preds = inference_service.predict(tensor)
    scores = [p.score for p in preds]
    assert scores == sorted(scores, reverse=True)


def test_predict_batch(inference_service):
    tensor = np.zeros((3, 3, IMAGE_SIZE, IMAGE_SIZE), dtype=np.float32)
    batch_preds = inference_service.predict_batch(tensor)
    assert len(batch_preds) == 3
    assert all("electronics" in [p.name for p in preds] for preds in batch_preds)


def test_preprocess_shape(sample_image_bytes):
    image = preprocessing.decode_image(sample_image_bytes)
    tensor = preprocessing.preprocess(image)
    assert tensor.shape == (1, 3, IMAGE_SIZE, IMAGE_SIZE)
    assert tensor.dtype == np.float32


def test_content_hash_is_deterministic(sample_image_bytes):
    image = preprocessing.decode_image(sample_image_bytes)
    tensor = preprocessing.preprocess(image)
    assert preprocessing.content_hash(tensor) == preprocessing.content_hash(tensor)


def test_decode_invalid_raises():
    import pytest
    from app.services.preprocessing import UnsupportedImageError

    with pytest.raises(UnsupportedImageError):
        preprocessing.decode_image(b"not an image")
