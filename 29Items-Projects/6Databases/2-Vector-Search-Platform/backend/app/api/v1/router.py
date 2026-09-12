"""Aggregate all v1 endpoint routers under a single ``api_router``."""

from fastapi import APIRouter

from app.api.v1.endpoints import benchmarks, documents, health, search

api_router = APIRouter()
api_router.include_router(health.router, tags=["health"])
api_router.include_router(documents.router, prefix="/documents", tags=["documents"])
api_router.include_router(search.router, prefix="/search", tags=["search"])
api_router.include_router(benchmarks.router, prefix="/benchmarks", tags=["benchmarks"])
