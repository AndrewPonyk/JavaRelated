"""Aggregate all v1 endpoint routers."""

from __future__ import annotations

from fastapi import APIRouter

from app.api.v1.endpoints import auth, dicomweb, health, instances, ml, studies

api_router = APIRouter()
api_router.include_router(health.router, prefix="/health", tags=["health"])
api_router.include_router(auth.router, prefix="/auth", tags=["auth"])
api_router.include_router(studies.router, prefix="/studies", tags=["studies"])
api_router.include_router(instances.router, prefix="/instances", tags=["instances"])
api_router.include_router(ml.router, prefix="/ml", tags=["ml"])
# DICOMweb lives under its own conventional root.
api_router.include_router(dicomweb.router, prefix="/dicomweb", tags=["dicomweb"])
