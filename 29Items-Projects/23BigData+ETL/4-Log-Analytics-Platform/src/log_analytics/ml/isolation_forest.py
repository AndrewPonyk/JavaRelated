"""Isolation Forest wrapper with calibrated 0..1 scoring and save/load.

Why a wrapper: sklearn's `predict` bakes the threshold into the model (contamination).
Operations wants a *dial*, not a verdict — we expose a normalized score and let the
alerting configuration decide what pages (LA_ANOMALY_ALERT_THRESHOLD).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

from log_analytics.ml.features import FEATURE_COLUMNS

_EPS = 1e-9


@dataclass
class ModelMetadata:
    version: str
    trained_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    n_samples: int = 0
    feature_columns: list[str] = field(default_factory=lambda: list(FEATURE_COLUMNS))
    params: dict[str, Any] = field(default_factory=dict)
    metrics: dict[str, float] = field(default_factory=dict)


class LogAnomalyDetector:
    """Isolation Forest over per-window behavioral features (see ml/features.py)."""

    def __init__(
        self,
        n_estimators: int = 200,
        contamination: str | float = "auto",
        random_state: int = 42,
    ) -> None:
        from sklearn.ensemble import IsolationForest  # lazy: keep module import cheap

        self._model = IsolationForest(
            n_estimators=n_estimators,
            contamination=contamination,
            random_state=random_state,
            n_jobs=-1,
        )
        self._feature_columns = list(FEATURE_COLUMNS)
        # Calibration bounds captured on training data → normalized inference scores.
        self._raw_min: float = 0.0
        self._raw_max: float = 1.0
        self._fitted = False

    def fit(self, features: pd.DataFrame) -> LogAnomalyDetector:
        matrix = self._matrix(features)
        self._model.fit(matrix)
        raw = -self._model.score_samples(matrix)  # higher = more anomalous
        self._raw_min = float(np.min(raw))
        self._raw_max = float(np.max(raw))
        self._fitted = True
        return self

    def score(self, features: pd.DataFrame) -> np.ndarray:
        """Normalized anomaly score in [0, 1]; 1 = most anomalous seen (or beyond).

        Min-max calibration against the training distribution — simple and monotonic.
        TODO: replace with quantile calibration (e.g. score = empirical CDF) for
        threshold stability across retrains.
        """
        self._require_fitted()
        raw = -self._model.score_samples(self._matrix(features))
        span = max(self._raw_max - self._raw_min, _EPS)
        return np.clip((raw - self._raw_min) / span, 0.0, 1.0)

    def save(self, path: str | Path) -> None:
        import joblib

        self._require_fitted()
        payload = {
            "model": self._model,
            "feature_columns": self._feature_columns,
            "raw_min": self._raw_min,
            "raw_max": self._raw_max,
        }
        joblib.dump(payload, Path(path))

    @classmethod
    def load(cls, path: str | Path) -> LogAnomalyDetector:
        import joblib

        payload = joblib.load(Path(path))
        detector = cls.__new__(cls)
        detector._model = payload["model"]
        detector._feature_columns = payload["feature_columns"]
        detector._raw_min = payload["raw_min"]
        detector._raw_max = payload["raw_max"]
        detector._fitted = True
        return detector

    # ── internals ─────────────────────────────────────────────────────────

    def _matrix(self, features: pd.DataFrame) -> np.ndarray:
        missing = [c for c in self._feature_columns if c not in features.columns]
        if missing:
            raise ValueError(f"feature frame missing columns: {missing}")
        return features[self._feature_columns].to_numpy(dtype=float)

    def _require_fitted(self) -> None:
        if not self._fitted:
            raise RuntimeError("detector is not fitted — call fit() or load()")
