"""Catalog (write-side) API contract."""

from datetime import datetime
from decimal import Decimal
from typing import Any
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.schemas.common import PageMeta


class ProductBase(BaseModel):
    sku: str = Field(min_length=1, max_length=64)
    name: str = Field(min_length=1, max_length=255)
    description: str | None = None
    brand: str | None = Field(default=None, max_length=128)
    category_id: UUID | None = None
    price: Decimal = Field(ge=0, decimal_places=2)
    attributes: dict[str, Any] = Field(default_factory=dict)
    in_stock: bool = True


class ProductCreate(ProductBase):
    pass


class ProductUpdate(BaseModel):
    """Partial update: omitted fields keep their current value. Nullable columns
    (description/brand/category_id) may be cleared with an explicit null; columns
    that are NOT NULL in the database reject explicit null with a 422 instead of
    surfacing a 500 from the constraint."""

    name: str | None = Field(default=None, min_length=1, max_length=255)
    description: str | None = None
    brand: str | None = Field(default=None, max_length=128)
    category_id: UUID | None = None
    price: Decimal | None = Field(default=None, ge=0, decimal_places=2)
    attributes: dict[str, Any] | None = None
    in_stock: bool | None = None

    @field_validator("name", "price", "attributes", "in_stock", mode="before")
    @classmethod
    def _reject_explicit_null(cls, value: Any) -> Any:
        # Runs only for fields present in the payload — defaults are not validated.
        if value is None:
            raise ValueError("cannot be null — omit the field to keep its current value")
        return value


class ProductRead(ProductBase):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    popularity: float
    is_active: bool
    created_at: datetime
    updated_at: datetime


class ProductListResponse(BaseModel):
    items: list[ProductRead]
    meta: PageMeta
