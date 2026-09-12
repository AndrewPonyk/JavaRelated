"""Application factory. Run: uvicorn app.main:app --reload"""

from __future__ import annotations

import asyncio
import contextlib
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware

from app.auth import require_auth
from app.config import get_settings
from app.middleware import RequestContextMiddleware
from app.routers import alerts, metrics, pipelines
from app.services.alert_service import get_alert_service
from app.services.alerts_consumer import AlertsConsumer


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Start/stop the background alerts-topic consumer with the app."""
    settings = get_settings()
    stop = asyncio.Event()
    task: asyncio.Task | None = None
    if settings.alerts_consumer_enabled:
        consumer = AlertsConsumer(settings, get_alert_service())
        task = asyncio.create_task(consumer.run(stop), name="alerts-consumer")
    yield
    stop.set()
    if task is not None:
        task.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await task


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(
        title="ETL Pipeline Builder API",
        version="1.0.0",
        description="Serving layer: sub-second metrics (Redis/WS), history "
        "(Redis hot window + Snowflake marts), pipeline registry CRUD, "
        "anomaly alert feed with acknowledgements.",
        lifespan=lifespan,
    )

    app.add_middleware(RequestContextMiddleware)
    app.add_middleware(GZipMiddleware, minimum_size=1024)  # history payloads compress ~10x
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins_list,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    protected = [Depends(require_auth)]
    app.include_router(metrics.router, prefix="/api/v1", dependencies=protected)
    app.include_router(pipelines.router, prefix="/api/v1", dependencies=protected)
    app.include_router(alerts.router, prefix="/api/v1", dependencies=protected)

    @app.get("/healthz", tags=["ops"])
    async def healthz() -> dict:
        return {"status": "ok", "service": settings.service_name}

    return app


app = create_app()
