"""Model loading with a two-tier registry and heuristic fallback.

Resolution order per alias (``champion``/``challenger``):

1. MLflow registry (when the package is installed and reachable),
2. the local model store under ``settings.model_dir``,
3. :class:`HeuristicStubModel` - a deterministic fallback that keeps the
   API scoring even with no registry at all. Falling back is a monitored
   resilience policy (``fraud_model_fallback_total`` + warning log), never
   a silent degradation.
"""

from __future__ import annotations

import hashlib
import math
import threading
from typing import Any, Protocol

import pandas as pd

from fraud_detection.core.config import Settings, get_settings
from fraud_detection.core.logging import get_logger
from fraud_detection.ml.features import FEATURE_COLUMNS
from fraud_detection.ml.registry import LocalModelStore
from fraud_detection.monitoring.metrics import FRAUD_MODEL_FALLBACK_TOTAL

logger = get_logger(__name__)


class ScoringModel(Protocol):
    """Protocol every servable fraud model must satisfy."""

    version: str
    source: str

    def predict_proba(self, features: dict[str, Any]) -> float:
        """Return the fraud probability in ``[0, 1]`` for one transaction."""
        ...


class HeuristicStubModel:
    """Deterministic fallback model used when no registry tier is available.

    Scores from the transaction amount plus a hash of the merchant
    category, clipped to ``[0, 1]``. Monotonically non-decreasing in the
    amount, which the model invariant tests assert.
    """

    source = "fallback"

    def __init__(self, alias: str = "champion") -> None:
        self.version = f"stub-{alias}"

    def predict_proba(self, features: dict[str, Any]) -> float:
        """Return a deterministic pseudo fraud probability."""
        amount = float(features.get("amount", 0.0) or 0.0)
        category = str(features.get("merchant_category", ""))
        category_risk = int(hashlib.sha256(category.encode("utf-8")).hexdigest(), 16) % 100 / 100.0
        amount_risk = math.log1p(max(amount, 0.0)) / 15.0
        score = 0.7 * amount_risk + 0.3 * category_risk
        return min(max(score, 0.0), 1.0)


class SklearnLocalModel:
    """Adapts a locally stored sklearn Pipeline to the ``ScoringModel`` protocol."""

    source = "local"

    def __init__(self, pipeline: Any, feature_names: list[str], version: str) -> None:
        self._pipeline = pipeline
        self._feature_names = feature_names or FEATURE_COLUMNS
        self.version = version

    def predict_proba(self, features: dict[str, Any]) -> float:
        """Score one transaction from its canonical feature dict."""
        row = {name: float(features.get(name, 0.0)) for name in self._feature_names}
        frame = pd.DataFrame([row], columns=self._feature_names)
        probability = float(self._pipeline.predict_proba(frame)[0, 1])
        return min(max(probability, 0.0), 1.0)


class _MLflowModelAdapter:
    """Adapts an MLflow pyfunc model to the ``ScoringModel`` protocol."""

    source = "mlflow"

    def __init__(self, pyfunc_model: Any, version: str) -> None:
        self._model = pyfunc_model
        self.version = version

    def predict_proba(self, features: dict[str, Any]) -> float:
        prediction = self._model.predict(pd.DataFrame([features]))
        value = prediction[0] if hasattr(prediction, "__getitem__") else prediction
        return float(min(max(float(value), 0.0), 1.0))


class ModelLoader:
    """Loads and caches models per registry alias (``champion``/``challenger``)."""

    def __init__(self, settings: Settings | None = None) -> None:
        self._settings_override = settings
        self._cache: dict[str, ScoringModel] = {}
        self._lock = threading.Lock()

    def _settings(self) -> Settings:
        return self._settings_override or get_settings()

    def get_model(self, alias: str = "champion") -> ScoringModel:
        """Return the model registered under ``alias``, loading it on first use."""
        with self._lock:
            if alias not in self._cache:
                self._cache[alias] = self._load(alias)
            return self._cache[alias]

    def invalidate(self, alias: str | None = None) -> None:
        """Drop cached models so the next request reloads from the registry.

        Called after promotions and completed retraining runs.

        Args:
            alias: Specific alias to invalidate, or None for all.
        """
        with self._lock:
            if alias is None:
                self._cache.clear()
            else:
                self._cache.pop(alias, None)

    def _load(self, alias: str) -> ScoringModel:
        settings = self._settings()

        model = self._load_from_mlflow(alias, settings)
        if model is not None:
            return model

        model = self._load_from_local_store(alias, settings)
        if model is not None:
            return model

        logger.warning(
            "model_fallback_engaged",
            alias=alias,
            model_name=settings.model_name,
            model_dir=settings.model_dir,
        )
        FRAUD_MODEL_FALLBACK_TOTAL.inc()
        return HeuristicStubModel(alias)

    def _load_from_mlflow(self, alias: str, settings: Settings) -> ScoringModel | None:
        """Tier 1: MLflow registry (lazy import; None on any failure)."""
        try:
            import mlflow  # noqa: PLC0415 - deliberate lazy import
            from mlflow.tracking import MlflowClient  # noqa: PLC0415

            mlflow.set_tracking_uri(settings.mlflow_tracking_uri)
            client = MlflowClient(tracking_uri=settings.mlflow_tracking_uri)
            model_version = client.get_model_version_by_alias(settings.model_name, alias)
            model_uri = f"models:/{settings.model_name}@{alias}"
            pyfunc_model = mlflow.pyfunc.load_model(model_uri)
            return _MLflowModelAdapter(pyfunc_model, version=str(model_version.version))
        except Exception:  # noqa: BLE001 - policy: degrade to the next tier
            return None

    def _load_from_local_store(self, alias: str, settings: Settings) -> ScoringModel | None:
        """Tier 2: versioned local model store (None when alias is absent)."""
        try:
            store = LocalModelStore(settings.model_dir)
            pipeline, metadata = store.load(alias)
            return SklearnLocalModel(
                pipeline,
                feature_names=list(metadata.get("feature_names", FEATURE_COLUMNS)),
                version=str(metadata.get("version", "unknown")),
            )
        except Exception:  # noqa: BLE001 - policy: degrade to the next tier
            return None
