"""API v1 route table."""

from fastapi import APIRouter

from app.api.v1.endpoints import auth, computations, ml, plots, symbolic

api_router = APIRouter()
api_router.include_router(auth.router, prefix="/auth", tags=["auth"])
api_router.include_router(symbolic.router, prefix="/symbolic", tags=["symbolic"])
api_router.include_router(plots.router, prefix="/plots", tags=["plots"])
api_router.include_router(ml.router, prefix="/ml", tags=["ml"])
api_router.include_router(computations.router, prefix="/computations", tags=["computations"])
