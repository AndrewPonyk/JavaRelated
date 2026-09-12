"""Finding query endpoints — filtering, pagination, severity stats."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import Principal, require_role
from app.db.session import get_session
from app.models.finding import Finding, Severity
from app.schemas.finding import FindingDetail, FindingsPage, SeverityStats

router = APIRouter()


@router.get("", response_model=FindingsPage, summary="List findings (filterable)")
async def list_findings(
    scan_id: int | None = Query(default=None),
    severity: Severity | None = Query(default=None),
    owasp_category: str | None = Query(default=None, pattern="^A0\\d:2021$"),
    page: int = Query(1, ge=1),
    page_size: int = Query(25, ge=1, le=200),
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(require_role("viewer")),
) -> FindingsPage:
    stmt = select(Finding)
    if scan_id is not None:
        stmt = stmt.where(Finding.scan_id == scan_id)
    if severity is not None:
        stmt = stmt.where(Finding.severity == severity)
    if owasp_category is not None:
        stmt = stmt.where(Finding.owasp_category == owasp_category)

    total = (await session.execute(select(func.count()).select_from(stmt.subquery()))).scalar_one()
    rows = (
        await session.scalars(
            stmt.order_by(Finding.id.desc()).offset((page - 1) * page_size).limit(page_size)
        )
    ).all()
    return FindingsPage(items=rows, total=total, page=page, page_size=page_size)


@router.get("/{finding_id}", response_model=FindingDetail, summary="Get finding detail")
async def get_finding(
    finding_id: int,
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(require_role("viewer")),
) -> Finding:
    finding = await session.get(Finding, finding_id)
    if finding is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, f"Finding {finding_id} not found")
    return finding


@router.get("/stats/severity", response_model=list[SeverityStats], summary="Severity histogram")
async def severity_stats(
    scan_id: int | None = Query(default=None),
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(require_role("viewer")),
) -> list[SeverityStats]:
    stmt = select(Finding.severity, func.count(Finding.id)).group_by(Finding.severity)
    if scan_id is not None:
        stmt = stmt.where(Finding.scan_id == scan_id)
    rows = (await session.execute(stmt)).all()
    return [SeverityStats(severity=sev, count=count) for sev, count in rows]
