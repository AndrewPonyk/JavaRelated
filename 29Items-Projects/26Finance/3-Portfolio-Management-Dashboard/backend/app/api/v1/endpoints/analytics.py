"""Analytics endpoints: performance attribution, backtest, and CSV report."""

from __future__ import annotations

import csv
import io

from fastapi import APIRouter, Depends, Query
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session

from app.api.deps import get_current_user, get_db, get_settings_dep
from app.core.config import Settings
from app.models.user import User
from app.schemas.analytics import AttributionResponse, BacktestRequest, BacktestResponse
from app.services import portfolio_service

router = APIRouter(prefix="/portfolios/{portfolio_id}", tags=["analytics"])


@router.get("/attribution", response_model=AttributionResponse)
def attribution(
    portfolio_id: int,
    lookback_days: int = Query(default=252, ge=30, le=2520),
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
    settings: Settings = Depends(get_settings_dep),
):
    """Per-asset return and risk contributions."""
    portfolio = portfolio_service.get_portfolio(db, portfolio_id, user)
    return portfolio_service.compute_attribution(db, portfolio, lookback_days, settings)


@router.post("/backtest", response_model=BacktestResponse)
def backtest(
    portfolio_id: int,
    request: BacktestRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
    settings: Settings = Depends(get_settings_dep),
):
    """Walk-forward rebalanced backtest vs. an equal-weight benchmark."""
    portfolio = portfolio_service.get_portfolio(db, portfolio_id, user)
    return portfolio_service.run_backtest(db, portfolio, request, settings)


@router.get("/report.csv")
def report_csv(
    portfolio_id: int,
    lookback_days: int = Query(default=252, ge=30, le=2520),
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
    settings: Settings = Depends(get_settings_dep),
):
    """Export a CSV report: headline risk metrics + per-asset attribution."""
    portfolio = portfolio_service.get_portfolio(db, portfolio_id, user)
    metrics = portfolio_service.compute_risk_metrics(db, portfolio, lookback_days, settings)
    attribution = portfolio_service.compute_attribution(db, portfolio, lookback_days, settings)

    buffer = io.StringIO()
    writer = csv.writer(buffer)
    writer.writerow(["Portfolio", portfolio.name])
    writer.writerow([])
    writer.writerow(["Metric", "Value"])
    for key, value in metrics.model_dump().items():
        writer.writerow([key, value])
    writer.writerow([])
    writer.writerow(["Symbol", "Weight", "ReturnContribution", "RiskContribution", "PctOfRisk"])
    for asset in attribution.assets:
        writer.writerow(
            [
                asset.symbol,
                asset.weight,
                asset.return_contribution,
                asset.risk_contribution,
                asset.pct_of_risk,
            ]
        )

    headers = {"Content-Disposition": f'attachment; filename="portfolio_{portfolio_id}_report.csv"'}
    return StreamingResponse(iter([buffer.getvalue()]), media_type="text/csv", headers=headers)
