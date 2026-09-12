"""Service layer: the only module in the API allowed to import quantfinlib.

Keeps HTTP concerns out of numerics and gives tests one seam to mock.
"""

from __future__ import annotations

import numpy as np

from app.config import Settings
from app.schemas.options import GreeksRequest, ImpliedVolRequest, McPriceRequest, PriceRequest
from quantfinlib.options import black_scholes as bs
from quantfinlib.options import monte_carlo as mc
from quantfinlib.utils.validation import InvalidInputError


def price_chain(req: PriceRequest) -> list[float]:
    prices = bs.price(
        spot=req.spot,
        strike=np.asarray(req.strikes, dtype=np.float64),
        vol=req.vol,
        rate=req.rate,
        expiry=req.expiry,
        kind=req.kind,
    )
    return np.asarray(prices).tolist()


def greeks(req: GreeksRequest) -> dict[str, float]:
    return bs.greeks(req.spot, req.strike, req.vol, req.rate, req.expiry, req.kind)


def implied_vol(req: ImpliedVolRequest) -> float:
    return bs.implied_vol(req.price, req.spot, req.strike, req.rate, req.expiry, req.kind)


def mc_price(req: McPriceRequest, settings: Settings) -> dict:
    if req.n_paths > settings.mc_max_paths:
        raise InvalidInputError(
            f"n_paths {req.n_paths} exceeds server limit {settings.mc_max_paths}"
        )
    seed = req.seed if req.seed is not None else settings.mc_default_seed
    result = mc.price_european(
        req.spot,
        req.strike,
        req.vol,
        req.rate,
        req.expiry,
        req.kind,
        n_paths=req.n_paths,
        seed=seed,
        antithetic=req.antithetic,
    )
    ci_low, ci_high = result.confidence_interval()
    return {
        "price": result.price,
        "std_error": result.std_error,
        "ci_low": ci_low,
        "ci_high": ci_high,
        "n_paths": result.n_paths,
        "seed": seed,
    }
