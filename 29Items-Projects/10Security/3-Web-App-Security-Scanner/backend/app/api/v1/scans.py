"""Scan lifecycle endpoints — thin routers; all logic in services."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import assert_target_allowed
from app.core.metrics import metrics
from app.core.ratelimit import scan_rate_limit
from app.core.security import Principal, require_role
from app.db.session import get_session
from app.models.finding import Finding
from app.models.scan import Scan, ScanStatus
from app.schemas.scan import ScanCreate, ScanProgress, ScanRead
from app.services.scan_orchestrator import orchestrator

router = APIRouter()


@router.post(
    "",
    response_model=ScanRead,
    status_code=status.HTTP_201_CREATED,
    summary="Create and launch a scan",
)
async def create_scan(
    payload: ScanCreate,
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(require_role("scanner")),
    _rate: None = Depends(scan_rate_limit),
) -> Scan:
    """Validate scope, persist the scan row, dispatch to the orchestrator.

    Returns immediately — progress is polled via GET /scans/{id}/progress.
    """
    await assert_target_allowed(str(payload.target_url), session)

    requested_by = (
        int(principal.id) if principal.auth_type == "jwt" and principal.id.isdigit() else None
    )
    scan = Scan(
        target_url=str(payload.target_url),
        profile=payload.profile,
        requested_by=requested_by,
    )
    session.add(scan)
    await session.commit()
    await session.refresh(scan)

    metrics.count("scans_total", labels={"status": "launched", "profile": payload.profile.value})
    await orchestrator.dispatch(scan.id)  # non-blocking; work continues in background
    return scan


@router.get("", response_model=list[ScanRead], summary="List scans")
async def list_scans(
    status_filter: ScanStatus | None = Query(default=None, alias="status"),
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(require_role("viewer")),
) -> list[Scan]:
    stmt = select(Scan).order_by(Scan.created_at.desc())
    if status_filter is not None:
        stmt = stmt.where(Scan.status == status_filter)
    stmt = stmt.offset((page - 1) * page_size).limit(page_size)
    return list((await session.scalars(stmt)).all())


@router.get("/{scan_id}", response_model=ScanRead, summary="Get scan details")
async def get_scan(
    scan_id: int,
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(require_role("viewer")),
) -> Scan:
    scan = await session.get(Scan, scan_id)
    if scan is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, f"Scan {scan_id} not found")
    return scan


@router.get("/{scan_id}/progress", response_model=ScanProgress, summary="Poll scan progress")
async def scan_progress(
    scan_id: int,
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(require_role("viewer")),
) -> ScanProgress:
    if await session.get(Scan, scan_id) is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, f"Scan {scan_id} not found")
    return await orchestrator.progress(scan_id, session)


@router.post("/{scan_id}/cancel", response_model=ScanRead, summary="Cancel a running scan")
async def cancel_scan(
    scan_id: int,
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(require_role("scanner")),
) -> Scan:
    scan = await session.get(Scan, scan_id)
    if scan is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, f"Scan {scan_id} not found")
    if scan.is_terminal:
        raise HTTPException(status.HTTP_409_CONFLICT, f"Scan already {scan.status.value}")
    await orchestrator.cancel(scan.id)
    await session.refresh(scan)
    return scan


@router.get("/{scan_id}/findings/count", summary="Finding count per severity")
async def scan_finding_counts(
    scan_id: int,
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(require_role("viewer")),
) -> dict[str, int]:
    if await session.get(Scan, scan_id) is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, f"Scan {scan_id} not found")
    stmt = (
        select(Finding.severity, func.count(Finding.id))
        .where(Finding.scan_id == scan_id)
        .group_by(Finding.severity)
    )
    rows = (await session.execute(stmt)).all()
    return {severity.value: count for severity, count in rows}
