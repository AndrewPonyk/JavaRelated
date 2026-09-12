"""Aggregates every v1 endpoint router into a single APIRouter."""

from __future__ import annotations

from fastapi import APIRouter

from app.api.v1.endpoints import (
    analytics,
    assets,
    auth,
    jobs,
    optimization,
    portfolios,
    risk,
)

api_router = APIRouter()
api_router.include_router(auth.router)
api_router.include_router(assets.router)
api_router.include_router(portfolios.router)
api_router.include_router(optimization.router)
api_router.include_router(risk.router)
api_router.include_router(analytics.router)
api_router.include_router(jobs.router)
