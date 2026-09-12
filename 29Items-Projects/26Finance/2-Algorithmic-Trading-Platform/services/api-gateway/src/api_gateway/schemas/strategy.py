"""Request/response schemas for the strategy resource (input validation layer).

Pydantic enforces the contract at the API edge so invalid data never reaches the
service/DB layer (ARCHITECTURE §2.5).
"""

from datetime import datetime
from enum import Enum
from typing import Annotated
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class StrategyState(str, Enum):
    DRAFT = "DRAFT"
    BACKTESTING = "BACKTESTING"
    PAPER = "PAPER"
    LIVE = "LIVE"
    HALTED = "HALTED"


class StrategyCreate(BaseModel):
    """Payload to register a new strategy."""

    # reject unknown fields; allow building by field name internally
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    name: str = Field(min_length=3, max_length=64, pattern=r"^[A-Za-z0-9 _\-]+$")
    klass: Annotated[
        str,
        Field(
            validation_alias="class",
            serialization_alias="class",
            description="Importable Strategy subclass path",
        ),
    ]
    symbols: list[str] = Field(min_length=1, max_length=50)
    params: dict[str, float | int | str] = Field(default_factory=dict)
    max_position_qty: int = Field(gt=0, le=1_000_000)
    max_order_notional: float = Field(gt=0)

    @property
    def normalized_symbols(self) -> list[str]:
        return [s.strip().upper() for s in self.symbols]


class StrategyUpdate(BaseModel):
    """Partial update — all fields optional. State transitions are validated server-side."""

    model_config = ConfigDict(extra="forbid")

    name: str | None = Field(default=None, min_length=3, max_length=64)
    params: dict[str, float | int | str] | None = None
    state: StrategyState | None = None
    max_position_qty: int | None = Field(default=None, gt=0, le=1_000_000)
    max_order_notional: float | None = Field(default=None, gt=0)


class StrategyOut(BaseModel):
    """Strategy as returned by the API."""

    model_config = ConfigDict(from_attributes=True, populate_by_name=True)

    id: UUID
    name: str
    klass: Annotated[str, Field(validation_alias="class", serialization_alias="class")]
    symbols: list[str]
    params: dict[str, float | int | str]
    state: StrategyState
    max_position_qty: int
    max_order_notional: float
    created_at: datetime
    updated_at: datetime
