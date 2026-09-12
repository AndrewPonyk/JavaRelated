"""Persistence for prediction audit records (drift / active-learning analysis).

Stores only the image hash + top prediction, never the raw image. Writes are best
effort: a logging failure must never break the classification response.
"""

from __future__ import annotations

from sqlalchemy.orm import Session

from app.core.logging import get_logger
from app.models.db_models import PredictionLog
from app.models.schemas import LabelPrediction

logger = get_logger(__name__)


def record_prediction(
    db: Session,
    image_hash: str,
    model_version: str,
    predictions: list[LabelPrediction],
) -> None:
    """Persist the top prediction for an image. Swallows errors (best effort)."""
    if not predictions:
        return
    top = predictions[0]
    try:
        db.add(
            PredictionLog(
                image_hash=image_hash,
                model_version=model_version,
                top_label=top.name,
                top_score=top.score,
            )
        )
        db.commit()
    except Exception as exc:  # noqa: BLE001
        db.rollback()
        logger.warning("prediction_log_failed", extra={"extra": {"error": str(exc)}})
