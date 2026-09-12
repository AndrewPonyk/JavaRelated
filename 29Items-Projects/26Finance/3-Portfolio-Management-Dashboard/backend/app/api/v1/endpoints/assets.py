"""Asset catalog endpoints + price ingestion (provider-backed) and a stub seeder."""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query, status
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.deps import get_current_user, get_db, get_settings_dep
from app.core.config import Settings
from app.core.exceptions import NotFoundError, ValidationError
from app.models.asset import Asset, PriceBar
from app.models.user import User
from app.schemas.portfolio import AssetRead
from app.services import market_data_service

router = APIRouter(prefix="/assets", tags=["assets"])


class AssetCreate(BaseModel):
    symbol: str = Field(min_length=1, max_length=32)
    name: str = Field(min_length=1, max_length=255)
    asset_class: str = "equity"
    currency: str = Field(default="USD", min_length=3, max_length=3)


@router.get("", response_model=list[AssetRead])
def list_assets(
    skip: int = Query(default=0, ge=0),
    limit: int = Query(default=100, ge=1, le=500),
    db: Session = Depends(get_db),
    _: User = Depends(get_current_user),
):
    stmt = select(Asset).order_by(Asset.symbol).offset(skip).limit(limit)
    return list(db.execute(stmt).scalars().all())


@router.get("/{asset_id}", response_model=AssetRead)
def get_asset(asset_id: int, db: Session = Depends(get_db), _: User = Depends(get_current_user)):
    asset = db.get(Asset, asset_id)
    if asset is None:
        raise NotFoundError(f"Asset {asset_id} not found")
    return asset


@router.post("", response_model=AssetRead, status_code=status.HTTP_201_CREATED)
def create_asset(
    payload: AssetCreate,
    db: Session = Depends(get_db),
    _: User = Depends(get_current_user),
):
    if db.execute(select(Asset).where(Asset.symbol == payload.symbol)).scalar_one_or_none():
        raise ValidationError(f"Asset {payload.symbol} already exists")
    asset = Asset(**payload.model_dump())
    db.add(asset)
    db.commit()
    db.refresh(asset)
    return asset


@router.post("/{asset_id}/ingest", status_code=status.HTTP_201_CREATED)
def ingest_prices(
    asset_id: int,
    days: int = 504,
    db: Session = Depends(get_db),
    _: User = Depends(get_current_user),
    settings: Settings = Depends(get_settings_dep),
):
    """Fetch and store price bars via the configured market-data provider."""
    asset = db.get(Asset, asset_id)
    if asset is None:
        raise NotFoundError(f"Asset {asset_id} not found")
    inserted = market_data_service.ingest_prices(db, asset, settings, days=days)
    return {
        "asset_id": asset_id,
        "bars_inserted": inserted,
        "provider": settings.market_data_provider,
    }


@router.post("/{asset_id}/seed-prices", status_code=status.HTTP_201_CREATED)
def seed_prices(
    asset_id: int,
    days: int = 504,
    db: Session = Depends(get_db),
    _: User = Depends(get_current_user),
):
    """Generate synthetic OHLC history for an asset (local/demo convenience)."""
    asset = db.get(Asset, asset_id)
    if asset is None:
        raise NotFoundError(f"Asset {asset_id} not found")
    series = market_data_service.generate_synthetic_prices(
        [asset.symbol], days=days, seed=market_data_service.symbol_seed(asset.symbol)
    )
    closes = series[asset.symbol]
    db.add_all(
        PriceBar(
            asset_id=asset.id,
            ts=ts.to_pydatetime(),
            open=float(close),
            high=float(close),
            low=float(close),
            close=float(close),
            volume=0.0,
        )
        for ts, close in closes.items()
    )
    db.commit()
    return {"asset_id": asset_id, "bars_inserted": len(closes)}
