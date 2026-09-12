"""Portfolio + holdings CRUD endpoints. All routes are scoped to the user."""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query, status
from sqlalchemy.orm import Session

from app.api.deps import get_current_user, get_db
from app.models.user import User
from app.schemas.portfolio import (
    HoldingCreate,
    HoldingRead,
    HoldingUpdate,
    PortfolioCreate,
    PortfolioRead,
    PortfolioUpdate,
)
from app.services import portfolio_service

router = APIRouter(prefix="/portfolios", tags=["portfolios"])


# --- Portfolios --------------------------------------------------------------
@router.post("", response_model=PortfolioRead, status_code=status.HTTP_201_CREATED)
def create_portfolio(
    payload: PortfolioCreate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    return portfolio_service.create_portfolio(db, user, payload)


@router.get("", response_model=list[PortfolioRead])
def list_portfolios(
    skip: int = Query(default=0, ge=0),
    limit: int = Query(default=50, ge=1, le=200),
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    return portfolio_service.list_portfolios(db, user, skip=skip, limit=limit)


@router.get("/{portfolio_id}", response_model=PortfolioRead)
def get_portfolio(
    portfolio_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    return portfolio_service.get_portfolio(db, portfolio_id, user)


@router.patch("/{portfolio_id}", response_model=PortfolioRead)
def update_portfolio(
    portfolio_id: int,
    payload: PortfolioUpdate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    return portfolio_service.update_portfolio(db, portfolio_id, user, payload)


@router.delete("/{portfolio_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_portfolio(
    portfolio_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    portfolio_service.delete_portfolio(db, portfolio_id, user)


# --- Holdings (sub-resource) -------------------------------------------------
@router.post(
    "/{portfolio_id}/holdings",
    response_model=HoldingRead,
    status_code=status.HTTP_201_CREATED,
)
def add_holding(
    portfolio_id: int,
    payload: HoldingCreate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    return portfolio_service.add_holding(db, portfolio_id, user, payload)


@router.patch("/{portfolio_id}/holdings/{holding_id}", response_model=HoldingRead)
def update_holding(
    portfolio_id: int,
    holding_id: int,
    payload: HoldingUpdate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    return portfolio_service.update_holding(db, portfolio_id, user, holding_id, payload)


@router.delete("/{portfolio_id}/holdings/{holding_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_holding(
    portfolio_id: int,
    holding_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    portfolio_service.delete_holding(db, portfolio_id, user, holding_id)
