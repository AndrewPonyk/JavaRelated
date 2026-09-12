"""Aggregates all v1 endpoint routers."""

from fastapi import APIRouter

from app.api.v1.endpoints import admin, auth, drugs, health, interactions, pharmacy

api_router = APIRouter()
api_router.include_router(health.router)
api_router.include_router(auth.router)
api_router.include_router(drugs.router)
api_router.include_router(interactions.router)
api_router.include_router(pharmacy.router)
api_router.include_router(admin.router)
