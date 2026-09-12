"""Audit trail — read-only access to application-level audit events."""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.core.security import get_current_principal
from app.db.session import get_db
from app.schemas.dataset import AuditEventList, AuditEventRead
from app.services import dataset_service

router = APIRouter(prefix="/audit", tags=["audit"], dependencies=[Depends(get_current_principal)])


@router.get("", response_model=AuditEventList)
def list_audit_events(
    entity_type: str | None = Query(default=None, max_length=64),
    entity_id: str | None = Query(default=None, max_length=64),
    actor: str | None = Query(default=None, max_length=254),
    limit: int = Query(default=50, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
) -> AuditEventList:
    items, total = dataset_service.list_audit_events(
        db,
        entity_type=entity_type,
        entity_id=entity_id,
        actor=actor,
        limit=limit,
        offset=offset,
    )
    return AuditEventList(
        items=[AuditEventRead.model_validate(item) for item in items],
        total=total,
        limit=limit,
        offset=offset,
    )
