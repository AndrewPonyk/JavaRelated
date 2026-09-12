"""Mean-variance optimization endpoints (the MPT core, exposed over HTTP)."""

from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.api.deps import get_current_user, get_db, get_settings_dep
from app.core.config import Settings
from app.models.user import User
from app.schemas.optimization import (
    FrontierRequest,
    FrontierResponse,
    OptimizationRequest,
    OptimizedPortfolioRead,
)
from app.services import portfolio_service

router = APIRouter(prefix="/portfolios/{portfolio_id}", tags=["optimization"])


@router.post("/optimize", response_model=OptimizedPortfolioRead)
def optimize_portfolio(
    portfolio_id: int,
    request: OptimizationRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
    settings: Settings = Depends(get_settings_dep),
):
    """Return optimal weights for the chosen objective (max-Sharpe / min-variance)."""
    portfolio = portfolio_service.get_portfolio(db, portfolio_id, user)
    return portfolio_service.optimize(db, portfolio, request, settings)


@router.post("/frontier", response_model=FrontierResponse)
def efficient_frontier(
    portfolio_id: int,
    request: FrontierRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
    settings: Settings = Depends(get_settings_dep),
):
    """Return the efficient frontier plus the tangency and min-variance portfolios."""
    portfolio = portfolio_service.get_portfolio(db, portfolio_id, user)
    return portfolio_service.build_frontier(db, portfolio, request, settings)
