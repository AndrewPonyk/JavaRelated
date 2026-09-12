"""v1 API surface. New endpoint modules register here."""

from fastapi import APIRouter

from app.api.v1.endpoints import admin, events, products, search, suggest

api_router = APIRouter()
api_router.include_router(search.router, prefix="/search", tags=["search"])
api_router.include_router(suggest.router, prefix="/suggest", tags=["search"])
api_router.include_router(events.router, prefix="/events", tags=["events"])
api_router.include_router(products.router, prefix="/products", tags=["catalog"])
api_router.include_router(admin.router, prefix="/admin", tags=["admin"])
