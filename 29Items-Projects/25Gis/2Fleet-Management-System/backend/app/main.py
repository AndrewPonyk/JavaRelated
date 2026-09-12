from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware

from app.api.errors import AppError, app_error_handler, unhandled_error_handler
from app.api.routes import drivers, geofences, health, routes, telemetry, trips, vehicles
from app.core.config import settings
from app.core.logging import configure_logging
from app.core.middleware import request_id_middleware, security_headers_middleware
from app.db.session import init_db


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    configure_logging()
    if settings.auto_create_tables:
        init_db()
    yield

app = FastAPI(
    title="Fleet Management System API",
    version="0.1.0",
    description="Real-time fleet tracking, geofencing, ETA, and route prediction API.",
    lifespan=lifespan,
)

app.add_middleware(GZipMiddleware, minimum_size=settings.gzip_minimum_size)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.middleware("http")(security_headers_middleware)
app.middleware("http")(request_id_middleware)
app.add_exception_handler(AppError, app_error_handler)
app.add_exception_handler(Exception, unhandled_error_handler)

app.include_router(health.router)
app.include_router(drivers.router, prefix="/api/v1")
app.include_router(geofences.router, prefix="/api/v1")
app.include_router(telemetry.router, prefix="/api/v1")
app.include_router(trips.router, prefix="/api/v1")
app.include_router(routes.router, prefix="/api/v1")
app.include_router(vehicles.router, prefix="/api/v1")
