"""Pricing endpoints.

Routers stay thin: schema in → service call → schema out. Plain `def` (not
async) for CPU-bound handlers so numerics run in the thread pool and the
event loop keeps serving (TECH-NOTES §3.6 #8). Every call is audit-logged
in a background task (after the response, off the latency path).
"""

from __future__ import annotations

import time

from fastapi import APIRouter, BackgroundTasks, Request

from app.dependencies import PriceCaller, SettingsDep
from app.schemas.options import (
    GreeksRequest,
    GreeksResponse,
    ImpliedVolRequest,
    ImpliedVolResponse,
    McPriceRequest,
    McPriceResponse,
    PriceRequest,
    PriceResponse,
)
from app.services import pricing_service
from app.services.audit import record_audit

router = APIRouter()


def _audit(
    background: BackgroundTasks,
    request: Request,
    caller: str,
    body,
    latency_ms: float,
    seed: int | None = None,
) -> None:
    background.add_task(
        record_audit,
        request_id=request.state.request_id,
        caller=caller,
        endpoint=request.url.path,
        params=body.model_dump(),
        latency_ms=latency_ms,
        seed=seed,
    )


@router.post("/price", response_model=PriceResponse)
def price_options(
    body: PriceRequest, request: Request, caller: PriceCaller, background: BackgroundTasks
) -> PriceResponse:
    """Price a chain of European options (one call, whole chain — batch-first)."""
    start = time.perf_counter()
    prices = pricing_service.price_chain(body)
    _audit(background, request, caller, body, (time.perf_counter() - start) * 1000)
    return PriceResponse(prices=prices, request_id=request.state.request_id)


@router.post("/greeks", response_model=GreeksResponse)
def greeks(
    body: GreeksRequest, request: Request, caller: PriceCaller, background: BackgroundTasks
) -> GreeksResponse:
    start = time.perf_counter()
    g = pricing_service.greeks(body)
    _audit(background, request, caller, body, (time.perf_counter() - start) * 1000)
    return GreeksResponse(**g, request_id=request.state.request_id)


@router.post("/implied-vol", response_model=ImpliedVolResponse)
def implied_vol(
    body: ImpliedVolRequest, request: Request, caller: PriceCaller, background: BackgroundTasks
) -> ImpliedVolResponse:
    start = time.perf_counter()
    iv = pricing_service.implied_vol(body)
    _audit(background, request, caller, body, (time.perf_counter() - start) * 1000)
    return ImpliedVolResponse(implied_vol=iv, request_id=request.state.request_id)


@router.post("/mc-price", response_model=McPriceResponse)
def mc_price(
    body: McPriceRequest,
    request: Request,
    caller: PriceCaller,
    settings: SettingsDep,
    background: BackgroundTasks,
) -> McPriceResponse:
    start = time.perf_counter()
    result = pricing_service.mc_price(body, settings)
    _audit(
        background, request, caller, body, (time.perf_counter() - start) * 1000, seed=result["seed"]
    )
    return McPriceResponse(**result, request_id=request.state.request_id)
