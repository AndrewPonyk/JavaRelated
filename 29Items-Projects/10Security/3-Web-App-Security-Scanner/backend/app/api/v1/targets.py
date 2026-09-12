"""Scan-target allowlist CRUD — the operator's scope control.

Targets are evaluated by assert_target_allowed on every scan creation
(app/api/deps.py). Empty allowlist = bootstrap mode (any host allowed,
network ranges still enforced).
"""

from __future__ import annotations

from datetime import UTC, datetime

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import Principal, require_role
from app.db.base import utcnow
from app.db.session import get_session
from app.models.scan import ScanTarget
from app.schemas.target import TargetCreate, TargetRead

router = APIRouter()


@router.get("", response_model=list[TargetRead], summary="List scan targets")
async def list_targets(
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(require_role("viewer")),
) -> list[ScanTarget]:
    return list((await session.scalars(select(ScanTarget).order_by(ScanTarget.id))).all())


@router.post(
    "",
    response_model=TargetRead,
    status_code=status.HTTP_201_CREATED,
    summary="Register an allowed target",
)
async def create_target(
    payload: TargetCreate,
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(require_role("admin")),
) -> ScanTarget:
    try:
        target = ScanTarget(
            host_pattern=payload.host_pattern,
            description=payload.description,
            verified_at=datetime.now(UTC),
        )
        session.add(target)
        await session.commit()
        await session.refresh(target)
        return target
    except IntegrityError as exc:
        await session.rollback()
        raise HTTPException(
            status.HTTP_409_CONFLICT,
            f"Pattern {payload.host_pattern!r} is already registered",
        ) from exc


@router.post(
    "/{target_id}/verify",
    response_model=TargetRead,
    summary="Mark a target as re-verified",
)
async def verify_target(
    target_id: int,
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(require_role("admin")),
) -> ScanTarget:
    """Refresh verified_at — operators re-confirm scope periodically."""
    target = await session.get(ScanTarget, target_id)
    if target is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, f"Target {target_id} not found")
    target.verified_at = utcnow()
    await session.commit()
    await session.refresh(target)
    return target


@router.delete(
    "/{target_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Remove an allowed target",
)
async def delete_target(
    target_id: int,
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(require_role("admin")),
) -> None:
    target = await session.get(ScanTarget, target_id)
    if target is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, f"Target {target_id} not found")
    await session.delete(target)
    await session.commit()
