"""Portfolio orchestration: CRUD + the bridge from stored data to the quant core.

This layer owns business rules (ownership enforcement, weight derivation,
constraint validation) and translates between persistence (`models`) and the
pure analytics functions. It raises domain exceptions; the API layer turns those
into HTTP responses.
"""

from __future__ import annotations

import numpy as np
import pandas as pd
from sqlalchemy import select
from sqlalchemy.orm import Session, selectinload

from app.analytics import (
    backtest_rebalanced,
    efficient_frontier,
    estimate_mu_sigma,
    maximum_sharpe_portfolio,
    minimum_variance_portfolio,
    performance_attribution,
    portfolio_returns,
    simulate_portfolio,
    summary,
    to_returns,
)
from app.core import cache
from app.core.config import Settings
from app.core.exceptions import DomainError, NotFoundError, ValidationError
from app.models.asset import Asset
from app.models.holding import Holding
from app.models.portfolio import Portfolio
from app.models.user import User
from app.schemas.analytics import AttributionResponse, BacktestRequest, BacktestResponse
from app.schemas.optimization import (
    FrontierPoint,
    FrontierRequest,
    FrontierResponse,
    Objective,
    OptimizationRequest,
    OptimizedPortfolioRead,
)
from app.schemas.portfolio import (
    HoldingCreate,
    HoldingUpdate,
    PortfolioCreate,
    PortfolioUpdate,
)
from app.schemas.risk import MonteCarloRequest, MonteCarloResponse, RiskMetricsResponse
from app.services import market_data_service

ESTIMATION_LOOKBACK = 504  # ~2y of daily bars for stable mu/cov estimation


# --------------------------------------------------------------------------- #
# CRUD: portfolios
# --------------------------------------------------------------------------- #
def _validate_assets_exist(db: Session, asset_ids: list[int]) -> None:
    if not asset_ids:
        return
    found = set(db.execute(select(Asset.id).where(Asset.id.in_(asset_ids))).scalars().all())
    missing = set(asset_ids) - found
    if missing:
        raise NotFoundError(f"Unknown asset id(s): {sorted(missing)}")


def create_portfolio(db: Session, user: User, data: PortfolioCreate) -> Portfolio:
    asset_ids = [h.asset_id for h in data.holdings]
    if len(asset_ids) != len(set(asset_ids)):
        raise ValidationError("Duplicate asset in holdings")
    _validate_assets_exist(db, asset_ids)

    portfolio = Portfolio(
        owner_id=user.id,
        name=data.name,
        description=data.description,
        base_currency=data.base_currency,
        holdings=[
            Holding(asset_id=h.asset_id, quantity=h.quantity, cost_basis=h.cost_basis)
            for h in data.holdings
        ],
    )
    db.add(portfolio)
    db.commit()
    db.refresh(portfolio)
    return portfolio


# Eager-load holdings AND each holding's asset to avoid N+1 queries when
# serializing PortfolioRead / deriving weights (which read holding.asset.symbol).
_PORTFOLIO_LOAD = selectinload(Portfolio.holdings).selectinload(Holding.asset)


def list_portfolios(db: Session, user: User, skip: int = 0, limit: int = 50) -> list[Portfolio]:
    stmt = (
        select(Portfolio)
        .where(Portfolio.owner_id == user.id)
        .options(_PORTFOLIO_LOAD)
        .order_by(Portfolio.created_at.desc())
        .offset(skip)
        .limit(limit)
    )
    return list(db.execute(stmt).scalars().all())


def get_portfolio(db: Session, portfolio_id: int, user: User) -> Portfolio:
    """Fetch a portfolio, enforcing ownership (defense in depth)."""
    stmt = select(Portfolio).where(Portfolio.id == portfolio_id).options(_PORTFOLIO_LOAD)
    portfolio = db.execute(stmt).scalar_one_or_none()
    if portfolio is None or portfolio.owner_id != user.id:
        # Don't reveal existence to non-owners.
        raise NotFoundError(f"Portfolio {portfolio_id} not found")
    return portfolio


def update_portfolio(
    db: Session, portfolio_id: int, user: User, data: PortfolioUpdate
) -> Portfolio:
    portfolio = get_portfolio(db, portfolio_id, user)
    if data.name is not None:
        portfolio.name = data.name
    if data.description is not None:
        portfolio.description = data.description
    db.commit()
    db.refresh(portfolio)
    return portfolio


def delete_portfolio(db: Session, portfolio_id: int, user: User) -> None:
    portfolio = get_portfolio(db, portfolio_id, user)
    db.delete(portfolio)
    db.commit()


# --------------------------------------------------------------------------- #
# CRUD: holdings (sub-resource)
# --------------------------------------------------------------------------- #
def add_holding(db: Session, portfolio_id: int, user: User, data: HoldingCreate) -> Holding:
    portfolio = get_portfolio(db, portfolio_id, user)
    _validate_assets_exist(db, [data.asset_id])
    if any(h.asset_id == data.asset_id for h in portfolio.holdings):
        raise ValidationError("Holding for this asset already exists; update it instead")
    holding = Holding(
        portfolio_id=portfolio.id,
        asset_id=data.asset_id,
        quantity=data.quantity,
        cost_basis=data.cost_basis,
    )
    db.add(holding)
    db.commit()
    db.refresh(holding)
    return holding


def _get_holding(db: Session, portfolio: Portfolio, holding_id: int) -> Holding:
    holding = db.get(Holding, holding_id)
    if holding is None or holding.portfolio_id != portfolio.id:
        raise NotFoundError(f"Holding {holding_id} not found")
    return holding


def update_holding(
    db: Session,
    portfolio_id: int,
    user: User,
    holding_id: int,
    data: HoldingUpdate,
) -> Holding:
    portfolio = get_portfolio(db, portfolio_id, user)
    holding = _get_holding(db, portfolio, holding_id)
    if data.quantity is not None:
        holding.quantity = data.quantity
    if data.cost_basis is not None:
        holding.cost_basis = data.cost_basis
    db.commit()
    db.refresh(holding)
    return holding


def delete_holding(db: Session, portfolio_id: int, user: User, holding_id: int) -> None:
    portfolio = get_portfolio(db, portfolio_id, user)
    holding = _get_holding(db, portfolio, holding_id)
    db.delete(holding)
    db.commit()


# --------------------------------------------------------------------------- #
# Quant bridge
# --------------------------------------------------------------------------- #
def _compute_bounds(
    allow_short: bool, min_weight: float, max_weight: float | None, n_assets: int
) -> tuple[float, float]:
    """Translate request constraints into per-asset (low, high) weight bounds.

    Validates feasibility against the fully-invested constraint (weights sum=1).
    """
    if allow_short:
        return (-1.0, max_weight if max_weight is not None else 1.0)
    upper = max_weight if max_weight is not None else 1.0
    lower = min_weight
    if upper * n_assets < 1.0 - 1e-9:
        raise DomainError(
            f"max_weight={upper} is infeasible for {n_assets} assets (sum can't reach 1)"
        )
    if lower * n_assets > 1.0 + 1e-9:
        raise DomainError(f"min_weight={lower} is infeasible for {n_assets} assets (sum exceeds 1)")
    return (lower, upper)


def _portfolio_inputs(
    db: Session, portfolio: Portfolio, lookback_days: int
) -> tuple[pd.DataFrame, list[str], np.ndarray]:
    """Load aligned prices and derive current market-value weights."""
    if not portfolio.holdings:
        raise DomainError("Portfolio has no holdings to analyze")

    asset_ids = [h.asset_id for h in portfolio.holdings]
    prices = market_data_service.get_price_history(db, asset_ids, lookback_days)
    symbols = list(prices.columns)

    qty_by_symbol = {h.asset.symbol: float(h.quantity) for h in portfolio.holdings}
    latest = prices.iloc[-1].to_numpy()
    quantities = np.array([qty_by_symbol.get(s, 0.0) for s in symbols])
    market_values = quantities * latest
    total = market_values.sum()
    # Fall back to equal weight if positions/prices are degenerate.
    weights = np.full(len(symbols), 1.0 / len(symbols)) if total <= 0 else market_values / total
    return prices, symbols, weights


def optimize(
    db: Session, portfolio: Portfolio, req: OptimizationRequest, settings: Settings
) -> OptimizedPortfolioRead:
    prices, symbols, _ = _portfolio_inputs(db, portfolio, req.lookback_days)
    mu, cov, labels = estimate_mu_sigma(prices, shrink=req.covariance_shrinkage)
    rf = req.risk_free_rate if req.risk_free_rate is not None else settings.risk_free_rate
    bounds = _compute_bounds(req.allow_short, req.min_weight, req.max_weight, len(symbols))

    if req.objective == Objective.MIN_VARIANCE:
        result = minimum_variance_portfolio(mu, cov, rf, bounds, labels)
    else:
        result = maximum_sharpe_portfolio(mu, cov, rf, bounds, labels)
    return OptimizedPortfolioRead(objective=req.objective, **result.as_dict())


def build_frontier(
    db: Session, portfolio: Portfolio, req: FrontierRequest, settings: Settings
) -> FrontierResponse:
    prices, symbols, _ = _portfolio_inputs(db, portfolio, req.lookback_days)

    # Cache deterministic frontiers keyed by inputs (fail-open if Redis is down).
    fingerprint = {
        "portfolio_id": portfolio.id,
        "req": req.model_dump(),
        "data": f"{prices.index[-1]}:{len(prices)}",
    }
    cache_key = cache.make_key("frontier", fingerprint)
    cached = cache.get_json(cache_key)
    if cached is not None:
        return FrontierResponse(**cached)

    mu, cov, labels = estimate_mu_sigma(prices, shrink=req.covariance_shrinkage)
    rf = settings.risk_free_rate
    bounds = _compute_bounds(req.allow_short, req.min_weight, req.max_weight, len(symbols))

    frontier = efficient_frontier(mu, cov, req.n_points, rf, bounds, labels)
    points = [
        FrontierPoint(
            expected_return=p.expected_return,
            volatility=p.volatility,
            sharpe=p.sharpe,
            weights=p.as_dict()["weights"],
        )
        for p in frontier
    ]
    max_sharpe = maximum_sharpe_portfolio(mu, cov, rf, bounds, labels)
    min_var = minimum_variance_portfolio(mu, cov, rf, bounds, labels)
    response = FrontierResponse(
        points=points,
        max_sharpe=OptimizedPortfolioRead(objective=Objective.MAX_SHARPE, **max_sharpe.as_dict()),
        min_variance=OptimizedPortfolioRead(objective=Objective.MIN_VARIANCE, **min_var.as_dict()),
    )
    cache.set_json(cache_key, response.model_dump(), ttl=settings.cache_ttl_seconds)
    return response


def compute_risk_metrics(
    db: Session, portfolio: Portfolio, lookback_days: int, settings: Settings
) -> RiskMetricsResponse:
    prices, _, weights = _portfolio_inputs(db, portfolio, lookback_days)
    rets = to_returns(prices, method="simple")
    port_rets = portfolio_returns(rets, weights)
    metrics = summary(
        port_rets,
        risk_free_rate=settings.risk_free_rate,
        periods_per_year=settings.trading_days_per_year,
    )
    return RiskMetricsResponse(**metrics)


def compute_attribution(
    db: Session, portfolio: Portfolio, lookback_days: int, settings: Settings
) -> AttributionResponse:
    prices, symbols, weights = _portfolio_inputs(db, portfolio, lookback_days)
    rets = to_returns(prices, method="simple")
    asset_cum = (rets + 1.0).prod().to_numpy() - 1.0
    _, cov, _ = estimate_mu_sigma(prices)
    result = performance_attribution(symbols, weights, asset_cum, cov)
    return AttributionResponse(**result.as_dict())


def run_backtest(
    db: Session, portfolio: Portfolio, req: BacktestRequest, settings: Settings
) -> BacktestResponse:
    prices, _, _ = _portfolio_inputs(db, portfolio, req.lookback_days)
    try:
        result = backtest_rebalanced(
            prices,
            lookback=req.lookback,
            rebalance_every=req.rebalance_every,
            objective=req.objective,
            risk_free_rate=settings.risk_free_rate,
            allow_short=req.allow_short,
            periods_per_year=settings.trading_days_per_year,
        )
    except ValueError as exc:
        raise DomainError(str(exc)) from exc
    return BacktestResponse(**result.as_dict())


def run_monte_carlo(
    db: Session, portfolio: Portfolio, req: MonteCarloRequest, settings: Settings
) -> MonteCarloResponse:
    """Synchronous Monte Carlo compute. Invoked by the job runner (inline or via
    the Celery worker); the HTTP layer dispatches it as a job (see job_service).
    """
    prices, _, weights = _portfolio_inputs(db, portfolio, ESTIMATION_LOOKBACK)
    mu, cov, _ = estimate_mu_sigma(prices)
    result = simulate_portfolio(
        mu,
        cov,
        weights,
        n_days=req.n_days,
        n_sims=req.n_sims,
        initial_value=req.initial_value,
        var_level=req.var_level,
        periods_per_year=settings.trading_days_per_year,
        seed=req.seed,
    )
    return MonteCarloResponse(**result.as_dict())
