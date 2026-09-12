"""Risk endpoints: VaR / Expected Shortfall for single series and portfolios."""

from __future__ import annotations

import time
from typing import Literal

import pandas as pd
from fastapi import APIRouter, BackgroundTasks, Request
from pydantic import BaseModel, Field, model_validator

from app.dependencies import PriceCaller
from app.services.audit import record_audit
from quantfinlib.risk import portfolio_var, value_at_risk

router = APIRouter()


class VarRequest(BaseModel):
    model_config = {
        "json_schema_extra": {
            "examples": [
                {
                    "returns": [0.012, -0.008, 0.004, -0.021, 0.009, -0.013],
                    "confidence": 0.99,
                    "method": "historical",
                }
            ]
        }
    }

    returns: list[float] = Field(
        min_length=2, max_length=1_000_000, description="Period returns, e.g. daily simple returns"
    )
    confidence: float = Field(default=0.99, gt=0, lt=1)
    method: Literal["historical", "parametric"] = "historical"


class PortfolioVarRequest(BaseModel):
    # Assets as columns: {"AAPL": [r1, r2, ...], "TLT": [...]}, equal lengths.
    returns: dict[str, list[float]] = Field(min_length=1, max_length=500)
    weights: dict[str, float]
    confidence: float = Field(default=0.99, gt=0, lt=1)
    method: Literal["historical", "parametric"] = "historical"

    @model_validator(mode="after")
    def _consistent(self) -> PortfolioVarRequest:
        lengths = {len(v) for v in self.returns.values()}
        if len(lengths) != 1 or lengths.pop() < 2:
            raise ValueError("all return series must have equal length >= 2")
        if set(self.weights) != set(self.returns):
            raise ValueError("weights keys must match returns keys exactly")
        return self


class VarResponse(BaseModel):
    var: float
    expected_shortfall: float
    confidence: float
    method: str
    n_observations: int
    request_id: str


def _audit(
    background: BackgroundTasks, request: Request, caller: str, body, latency_ms: float
) -> None:
    background.add_task(
        record_audit,
        request_id=request.state.request_id,
        caller=caller,
        endpoint=request.url.path,
        params=body.model_dump(),
        latency_ms=latency_ms,
    )


@router.post("/var", response_model=VarResponse)
def compute_var(
    body: VarRequest, request: Request, caller: PriceCaller, background: BackgroundTasks
) -> VarResponse:
    start = time.perf_counter()
    report = value_at_risk(body.returns, confidence=body.confidence, method=body.method)
    _audit(background, request, caller, body, (time.perf_counter() - start) * 1000)
    return VarResponse(
        var=report.var,
        expected_shortfall=report.expected_shortfall,
        confidence=report.confidence,
        method=report.method,
        n_observations=report.n_observations,
        request_id=request.state.request_id,
    )


@router.post("/portfolio-var", response_model=VarResponse)
def compute_portfolio_var(
    body: PortfolioVarRequest, request: Request, caller: PriceCaller, background: BackgroundTasks
) -> VarResponse:
    start = time.perf_counter()
    frame = pd.DataFrame(body.returns)
    weights = [body.weights[col] for col in frame.columns]
    report = portfolio_var(frame, weights=weights, confidence=body.confidence, method=body.method)
    _audit(background, request, caller, body, (time.perf_counter() - start) * 1000)
    return VarResponse(
        var=report.var,
        expected_shortfall=report.expected_shortfall,
        confidence=report.confidence,
        method=report.method,
        n_observations=report.n_observations,
        request_id=request.state.request_id,
    )
