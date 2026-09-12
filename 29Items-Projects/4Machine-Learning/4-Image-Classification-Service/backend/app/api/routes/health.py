"""Liveness, readiness, and Prometheus metrics endpoints."""

from __future__ import annotations

from fastapi import APIRouter, Request, Response, status

from app.core.metrics import MODEL_INFO, render_latest

router = APIRouter(tags=["health"])


@router.get("/health")
def health() -> dict[str, str]:
    """Liveness: the process is up. Always cheap, never touches dependencies."""
    return {"status": "ok"}


@router.get("/ready")
def ready(request: Request, response: Response) -> dict[str, object]:
    """Readiness: returns 503 until the model is loaded so the LB won't route traffic."""
    service = getattr(request.app.state, "inference_service", None)
    if service is None:
        MODEL_INFO.set(0)
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
        return {"status": "loading", "model_loaded": False}
    MODEL_INFO.set(1)
    return {"status": "ready", "model_loaded": True, "model_version": service.model_version}


@router.get("/metrics")
def metrics() -> Response:
    """Prometheus metrics exposition."""
    body, content_type = render_latest()
    return Response(content=body, media_type=content_type)
