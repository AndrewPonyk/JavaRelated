"""Fraud model loading and feature pipeline.

The SAME feature transform must be used at train time and serve time to avoid
train/serve skew (see docs/TECH-NOTES.md §3.6). The trained artifact is loaded
from MODEL_PATH; if it is missing/unloadable we degrade to a transparent
heuristic so the service stays available (the PHP caller also fails open).
"""

from __future__ import annotations

import logging
import os
from dataclasses import dataclass

logger = logging.getLogger("fraud.model")

MODEL_PATH = os.getenv("MODEL_PATH", "/app/model_artifacts/fraud_model.pkl")


@dataclass(frozen=True)
class Features:
    amount_minor: int
    item_count: int
    distinct_sellers: int
    currency: str

    @classmethod
    def from_payload(cls, raw: dict) -> "Features":
        return cls(
            amount_minor=int(raw.get("amount_minor", 0)),
            item_count=int(raw.get("item_count", 0)),
            distinct_sellers=int(raw.get("distinct_sellers", 0)),
            currency=str(raw.get("currency", "USD")),
        )

    def to_vector(self) -> list[float]:
        """Order MUST match the training pipeline's column order."""
        return [
            float(self.amount_minor),
            float(self.item_count),
            float(self.distinct_sellers),
        ]


class FraudModel:
    """Wraps the trained estimator with a safe fallback."""

    def __init__(self) -> None:
        self._estimator = self._load()
        # Surfaced in the API response so callers can audit which model scored them.
        self.version = "heuristic-0.1.0" if self._estimator is None else "model-0.1.0"

    @staticmethod
    def _load():
        try:
            import joblib  # imported lazily so the service boots without the artifact

            model = joblib.load(MODEL_PATH)
            logger.info("Loaded fraud model from %s", MODEL_PATH)
            return model
        except Exception as exc:  # noqa: BLE001 - degrade gracefully
            logger.warning("No model at %s (%s); using heuristic fallback.", MODEL_PATH, exc)
            return None

    def score(self, features: Features) -> float:
        """Return a fraud probability in [0, 1]."""
        if self._estimator is not None:
            # TODO: real estimator expects a 2D array of the training features.
            proba = float(self._estimator.predict_proba([features.to_vector()])[0][1])
            return max(0.0, min(1.0, proba))
        return self._heuristic(features)

    @staticmethod
    def _heuristic(features: Features) -> float:
        """Transparent stand-in: high amounts and many sellers look riskier."""
        score = 0.0
        if features.amount_minor > 200_000:  # > 2000.00
            score += 0.5
        if features.distinct_sellers >= 5:
            score += 0.3
        if features.item_count >= 20:
            score += 0.2
        return min(1.0, score)
