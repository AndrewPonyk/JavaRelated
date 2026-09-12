"""Read-only routes for positions, orders, and PnL."""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from api_gateway.dependencies import get_session
from api_gateway.schemas.portfolio import OrderOut, PnLSummary, PositionOut
from api_gateway.services.portfolio_service import PortfolioService

router = APIRouter(tags=["portfolio"])


def _service(session: AsyncSession = Depends(get_session)) -> PortfolioService:
    return PortfolioService(session)


@router.get("/positions", response_model=list[PositionOut])
async def list_positions(svc: PortfolioService = Depends(_service)) -> list[PositionOut]:
    return await svc.list_positions()


@router.get("/orders", response_model=list[OrderOut])
async def list_orders(
    limit: int = Query(default=100, ge=1, le=1000),
    offset: int = Query(default=0, ge=0),
    svc: PortfolioService = Depends(_service),
) -> list[OrderOut]:
    return await svc.list_orders(limit, offset)


@router.get("/pnl", response_model=PnLSummary)
async def pnl_summary(svc: PortfolioService = Depends(_service)) -> PnLSummary:
    return await svc.pnl_summary()
