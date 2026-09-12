from __future__ import annotations

from decimal import Decimal

from pydantic import BaseModel, ConfigDict, Field


class AssetRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    symbol: str
    name: str
    asset_class: str
    currency: str


class HoldingCreate(BaseModel):
    asset_id: int
    quantity: Decimal = Field(gt=0)
    cost_basis: Decimal = Field(ge=0, default=Decimal("0"))


class HoldingUpdate(BaseModel):
    quantity: Decimal | None = Field(default=None, gt=0)
    cost_basis: Decimal | None = Field(default=None, ge=0)


class HoldingRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    asset_id: int
    quantity: Decimal
    cost_basis: Decimal
    asset: AssetRead | None = None


class PortfolioCreate(BaseModel):
    name: str = Field(min_length=1, max_length=255)
    description: str | None = Field(default=None, max_length=1024)
    base_currency: str = Field(default="USD", min_length=3, max_length=3)
    holdings: list[HoldingCreate] = Field(default_factory=list)


class PortfolioUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=255)
    description: str | None = Field(default=None, max_length=1024)


class PortfolioRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    owner_id: int
    name: str
    description: str | None
    base_currency: str
    holdings: list[HoldingRead] = Field(default_factory=list)
