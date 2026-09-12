"""FastAPI dependency providers (module-level cached singletons)."""

from __future__ import annotations

from functools import lru_cache

from fraud_detection.db.session import get_db_session
from fraud_detection.services.ab_config import ABConfigStore
from fraud_detection.services.ab_router import ABRouter
from fraud_detection.services.drift_detector import DriftDetector
from fraud_detection.services.model_loader import ModelLoader
from fraud_detection.services.prediction_service import PredictionService
from fraud_detection.services.retraining import RetrainingService

__all__ = [
    "get_db_session",
    "get_ab_config_store",
    "get_model_loader",
    "get_prediction_service",
    "get_drift_detector",
    "get_retraining_service",
    "get_retraining_trigger",
    "reset_singletons",
]


@lru_cache(maxsize=1)
def get_ab_config_store() -> ABConfigStore:
    """Return the shared runtime A/B configuration store."""
    return ABConfigStore()


@lru_cache(maxsize=1)
def get_model_loader() -> ModelLoader:
    """Return the shared :class:`ModelLoader` singleton."""
    return ModelLoader()


@lru_cache(maxsize=1)
def get_retraining_service() -> RetrainingService:
    """Return the shared :class:`RetrainingService` singleton."""
    return RetrainingService(model_loader=get_model_loader())


@lru_cache(maxsize=1)
def get_prediction_service() -> PredictionService:
    """Return the shared :class:`PredictionService` singleton."""
    return PredictionService(
        ab_router=ABRouter(config_store=get_ab_config_store()),
        model_loader=get_model_loader(),
    )


@lru_cache(maxsize=1)
def get_drift_detector() -> DriftDetector:
    """Return the shared :class:`DriftDetector` singleton."""
    return DriftDetector(retraining=get_retraining_service())


# Backwards-compatible alias (earlier revisions used "trigger" terminology).
get_retraining_trigger = get_retraining_service


def reset_singletons() -> None:
    """Drop every cached singleton (test isolation helper)."""
    get_ab_config_store.cache_clear()
    get_model_loader.cache_clear()
    get_retraining_service.cache_clear()
    get_prediction_service.cache_clear()
    get_drift_detector.cache_clear()
