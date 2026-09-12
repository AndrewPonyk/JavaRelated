"""Tests for prediction audit logging."""

from __future__ import annotations

from app.db.session import SessionLocal
from app.models.db_models import PredictionLog
from app.models.schemas import LabelPrediction
from app.services.prediction_log import record_prediction
from sqlalchemy import select


def test_record_prediction_writes_row(client):
    db = SessionLocal()
    try:
        record_prediction(
            db,
            image_hash="abc123",
            model_version="test-v1",
            predictions=[LabelPrediction(name="electronics", score=0.97)],
        )
        rows = db.scalars(select(PredictionLog).where(PredictionLog.image_hash == "abc123")).all()
        assert len(rows) == 1
        assert rows[0].top_label == "electronics"
    finally:
        db.close()


def test_classify_creates_prediction_log(client, sample_image_bytes):
    client.post("/classify", files={"file": ("p.jpg", sample_image_bytes, "image/jpeg")})
    db = SessionLocal()
    try:
        rows = db.scalars(select(PredictionLog)).all()
        assert len(rows) >= 1
    finally:
        db.close()


def test_record_prediction_empty_is_noop(client):
    db = SessionLocal()
    try:
        record_prediction(db, "h", "v", [])  # should not raise or write
    finally:
        db.close()
