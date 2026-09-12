"""Request/response models for pricing endpoints.

First validation line (types/bounds); the library re-validates numerics at
its own boundary. Batch-first: `strikes` is always a list — one call prices
a whole chain.
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field, model_validator

Kind = Literal["call", "put"]

MAX_BATCH = 100_000  # request-size guard; larger books belong in direct library use


class PriceRequest(BaseModel):
    model_config = {
        "json_schema_extra": {
            "examples": [
                {
                    "spot": 100,
                    "strikes": [90, 100, 110],
                    "vol": 0.2,
                    "rate": 0.05,
                    "expiry": 1.0,
                    "kind": "call",
                }
            ]
        }
    }

    spot: float = Field(gt=0, description="Current underlying price")
    strikes: list[float] = Field(min_length=1, max_length=MAX_BATCH)
    vol: float = Field(gt=0, le=5.0, description="Implied volatility, e.g. 0.2")
    rate: float = Field(ge=-0.1, le=1.0, description="Continuously compounded risk-free rate")
    expiry: float = Field(gt=0, le=100, description="Time to expiry in years")
    kind: Kind = "call"

    @model_validator(mode="after")
    def _strikes_positive(self) -> PriceRequest:
        if any(k <= 0 for k in self.strikes):
            raise ValueError("all strikes must be > 0")
        return self


class PriceResponse(BaseModel):
    prices: list[float]
    request_id: str


class GreeksRequest(BaseModel):
    spot: float = Field(gt=0)
    strike: float = Field(gt=0)
    vol: float = Field(gt=0, le=5.0)
    rate: float = Field(ge=-0.1, le=1.0)
    expiry: float = Field(gt=0, le=100)
    kind: Kind = "call"


class GreeksResponse(BaseModel):
    delta: float
    gamma: float
    vega: float
    theta: float
    rho: float
    request_id: str


class ImpliedVolRequest(BaseModel):
    price: float = Field(gt=0)
    spot: float = Field(gt=0)
    strike: float = Field(gt=0)
    rate: float = Field(ge=-0.1, le=1.0)
    expiry: float = Field(gt=0, le=100)
    kind: Kind = "call"


class ImpliedVolResponse(BaseModel):
    implied_vol: float
    request_id: str


class McPriceRequest(BaseModel):
    model_config = {
        "json_schema_extra": {
            "examples": [
                {
                    "spot": 100,
                    "strike": 100,
                    "vol": 0.2,
                    "rate": 0.05,
                    "expiry": 1.0,
                    "kind": "call",
                    "n_paths": 100000,
                    "seed": 42,
                }
            ]
        }
    }

    spot: float = Field(gt=0)
    strike: float = Field(gt=0)
    vol: float = Field(gt=0, le=5.0)
    rate: float = Field(ge=-0.1, le=1.0)
    expiry: float = Field(gt=0, le=100)
    kind: Kind = "call"
    n_paths: int = Field(default=100_000, gt=0)  # upper bound enforced vs settings.mc_max_paths
    seed: int | None = Field(default=None, description="Omit to use the server default seed")
    antithetic: bool = True


class McPriceResponse(BaseModel):
    price: float
    std_error: float
    ci_low: float
    ci_high: float
    n_paths: int
    seed: int  # echoed so the number is reproducible
    request_id: str
