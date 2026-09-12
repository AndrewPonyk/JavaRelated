"""Risk endpoints: headline metrics (sync) and Monte Carlo (async job)."""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query, status
from sqlalchemy.orm import Session

from app.api.deps import get_current_user, get_db, get_settings_dep
from app.core.config import Settings
from app.models.user import User
from app.schemas.job import JobAccepted
from app.schemas.risk import MonteCarloRequest, RiskMetricsResponse
from app.services import job_service, portfolio_service

router = APIRouter(prefix="/portfolios/{portfolio_id}", tags=["risk"])


@router.get("/risk", response_model=RiskMetricsResponse)
def risk_metrics(
    portfolio_id: int,
    lookback_days: int = Query(default=252, ge=30, le=2520),
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
    settings: Settings = Depends(get_settings_dep),
):
    """VaR/CVaR, Sharpe/Sortino, volatility and max drawdown for the portfolio."""
    portfolio = portfolio_service.get_portfolio(db, portfolio_id, user)
    return portfolio_service.compute_risk_metrics(db, portfolio, lookback_days, settings)


@router.post("/monte-carlo", response_model=JobAccepted, status_code=status.HTTP_202_ACCEPTED)
def monte_carlo(
    portfolio_id: int,
    request: MonteCarloRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Dispatch a correlated-GBM Monte Carlo simulation as an async job.

    Returns 202 + a ``job_id``; poll ``GET /api/v1/jobs/{job_id}`` for the result.
    """
    portfolio = portfolio_service.get_portfolio(db, portfolio_id, user)
    job = job_service.dispatch_monte_carlo(db, portfolio, user, request)
    return JobAccepted(job_id=job.id, status=job.status)
