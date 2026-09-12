"""Shared FastAPI dependencies (dependency injection providers).

Long-lived singletons (settings, inference service, cache) are created once during
the app lifespan and stored on ``app.state``; these providers expose them to routes.
"""

from __future__ import annotations

from fastapi import HTTPException, Request, status

from app.core.config import Settings, get_settings
from app.services.cache import CacheService
from app.services.inference import InferenceService


def settings_dep() -> Settings:
    return get_settings()


def get_inference_service(request: Request) -> InferenceService:
    """Return the process-wide InferenceService loaded at startup."""
    service: InferenceService | None = getattr(request.app.state, "inference_service", None)
    if service is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Model not loaded yet",
        )
    return service


def get_cache_service(request: Request) -> CacheService:
    """Return the process-wide CacheService loaded at startup."""
    return request.app.state.cache_service
