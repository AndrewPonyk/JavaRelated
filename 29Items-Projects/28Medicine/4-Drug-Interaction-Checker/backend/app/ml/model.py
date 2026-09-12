"""Severity model: loading + inference.

Two operating modes:

1. **Trained artifact** — if ``ML_MODEL_LOCAL_PATH`` points to a joblib file
   (produced by ``ml/training/train.py`` and synced from S3), it is loaded and
   used. Its ``predict_proba`` must output the 4 classes in ``_CLASS_TO_SEVERITY``
   order.
2. **Rule-based fallback** — a deterministic scorer over graph features. It
   computes a *risk score* from the drug-property graph and maps it to a severity
   band. Crucially, a pair with **no graph evidence** (no shared classes, no
   pathway proximity, no embedding similarity) scores ~0 and is reported as
   ``UNKNOWN`` so unrelated drugs are NOT surfaced as spurious interactions.
"""

from __future__ import annotations

import math
import os
import threading

from app.core.config import get_settings
from app.core.logging import get_logger
from app.models.common import Severity

logger = get_logger(__name__)

# Index order of the 4-class classifier output (used by the trained artifact).
_CLASS_TO_SEVERITY = [
    Severity.MINOR,
    Severity.MODERATE,
    Severity.MAJOR,
    Severity.CONTRAINDICATED,
]

# Rule-based risk-score -> severity bands (highest threshold first). A score
# below the lowest threshold means "no interaction evidence" -> UNKNOWN.
_RISK_BANDS: list[tuple[float, Severity]] = [
    (0.75, Severity.CONTRAINDICATED),
    (0.55, Severity.MAJOR),
    (0.35, Severity.MODERATE),
    (0.20, Severity.MINOR),
]


class SeverityModel:
    _instance: SeverityModel | None = None
    _lock = threading.Lock()

    def __init__(self) -> None:
        self._estimator = None  # loaded sklearn-like estimator, or None
        self._mode = "rule_based"

    @classmethod
    def instance(cls) -> SeverityModel:
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    inst = cls()
                    inst.load()
                    cls._instance = inst
        return cls._instance

    @classmethod
    def reset(cls) -> None:
        """Drop the cached singleton (used by tests)."""
        with cls._lock:
            cls._instance = None

    @property
    def mode(self) -> str:
        return self._mode

    def load(self) -> None:
        path = get_settings().ml_model_local_path
        if path and os.path.exists(path):
            try:
                import joblib  # imported lazily; not required for rule-based serving

                self._estimator = joblib.load(path)
                self._mode = "trained_artifact"
                logger.info("severity_model_loaded", path=path)
                return
            except Exception as exc:  # noqa: BLE001 - any failure -> safe fallback
                logger.warning("severity_model_load_failed", path=path, error=str(exc))
        self._estimator = None
        self._mode = "rule_based"
        logger.info("severity_model_rule_based")

    def predict_proba(self, features: list[float]) -> list[float]:
        if self._estimator is not None:
            proba = self._estimator.predict_proba([features])[0]
            return [float(p) for p in proba]
        return _score_to_distribution(self._risk_score(features))

    def predict(self, features: list[float]) -> tuple[Severity, float]:
        """Return (severity, confidence).

        - Trained model: argmax class + its probability.
        - Rule-based: risk-score band + the score itself as confidence; pairs
          with no graph evidence return (UNKNOWN, ~0).
        """
        if self._estimator is not None:
            proba = self.predict_proba(features)
            idx = max(range(len(proba)), key=lambda i: proba[i])
            return _CLASS_TO_SEVERITY[idx], float(proba[idx])

        score = self._risk_score(features)
        for threshold, severity in _RISK_BANDS:
            if score >= threshold:
                return severity, round(score, 4)
        return Severity.UNKNOWN, round(score, 4)

    @staticmethod
    def _risk_score(features: list[float]) -> float:
        """Map graph features -> risk score in [0, 1].

        features = [shared_class_count, graph_distance, embedding_cosine]
        More shared classes, closer pathway distance, and higher embedding
        similarity all raise the modeled interaction risk. No signal -> ~0.
        """
        shared, distance, cosine = (list(features) + [0.0, 0.0, 0.0])[:3]
        score = 0.0
        if distance == 1:
            score += 0.50
        elif distance == 2:  # ingredients sharing a class are 2 hops apart
            score += 0.30
        score += min(shared, 3.0) * 0.12
        score += max(cosine, 0.0) * 0.25
        return max(0.0, min(1.0, score))


def _score_to_distribution(score: float) -> list[float]:
    """Soft 4-class distribution around the score (for predict_proba); sums to 1."""
    centers = [0.15, 0.45, 0.70, 0.90]
    weights = [math.exp(-((score - c) ** 2) / 0.05) for c in centers]
    total = sum(weights) or 1.0
    return [w / total for w in weights]
