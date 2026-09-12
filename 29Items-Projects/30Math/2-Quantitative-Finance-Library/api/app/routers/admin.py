"""Admin endpoints (admin scope): read the calculation audit trail."""

from __future__ import annotations

from datetime import datetime

from fastapi import APIRouter, Query
from pydantic import BaseModel
from sqlalchemy import select

from app.db.models import CalculationAudit
from app.dependencies import AdminCaller, Db

router = APIRouter()


class AuditEntry(BaseModel):
    id: str
    request_id: str
    caller: str
    endpoint: str
    params_hash: str
    seed: int | None
    latency_ms: float
    created_at: datetime


class AuditPage(BaseModel):
    entries: list[AuditEntry]
    count: int
    offset: int
    limit: int


@router.get("/audit", response_model=AuditPage)
async def list_audit(
    caller: AdminCaller,
    db: Db,
    limit: int = Query(default=50, gt=0, le=1000),
    offset: int = Query(default=0, ge=0),
) -> AuditPage:
    """Calculation-audit rows, newest first, paginated with limit/offset."""
    rows = await db.execute(
        select(CalculationAudit)
        .order_by(CalculationAudit.created_at.desc())
        .offset(offset)
        .limit(limit)
    )
    entries = [AuditEntry.model_validate(row, from_attributes=True) for row in rows.scalars()]
    return AuditPage(entries=entries, count=len(entries), offset=offset, limit=limit)
