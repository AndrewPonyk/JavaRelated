"""Aggregate v1 API router."""

from fastapi import APIRouter

from app.api.v1 import auth, findings, reports, scans, targets, users

api_router = APIRouter()
api_router.include_router(auth.router, prefix="/auth", tags=["auth"])
api_router.include_router(scans.router, prefix="/scans", tags=["scans"])
api_router.include_router(findings.router, prefix="/findings", tags=["findings"])
api_router.include_router(reports.router, prefix="/reports", tags=["reports"])
api_router.include_router(targets.router, prefix="/targets", tags=["targets"])
api_router.include_router(users.router, prefix="/users", tags=["users"])
