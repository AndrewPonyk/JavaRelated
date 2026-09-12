"""Aggregates all v1 routers; mounted at /api/v1 in app.main."""

from fastapi import APIRouter

from app.api.v1 import anomalies, auth, devices, events, health, ingest, metrics

api_router = APIRouter()
api_router.include_router(health.router, prefix="/health", tags=["health"])
api_router.include_router(auth.router, prefix="/auth", tags=["auth"])
api_router.include_router(ingest.router, prefix="/ingest", tags=["ingest"])
api_router.include_router(devices.router, prefix="/devices", tags=["devices"])
api_router.include_router(metrics.router, prefix="/devices", tags=["metrics"])
api_router.include_router(anomalies.router, prefix="/devices", tags=["anomalies"])
api_router.include_router(events.router, prefix="/events", tags=["events"])
