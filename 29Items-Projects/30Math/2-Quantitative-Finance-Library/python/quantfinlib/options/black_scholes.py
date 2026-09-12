"""Vectorised Black-Scholes façade.

Accepts scalars or NumPy arrays (broadcast together); delegates the hot loop
to the C++ core when available, `_pure` otherwise.
"""

from __future__ import annotations

from typing import Literal

import numpy as np

from quantfinlib._compat import backend
from quantfinlib.utils.validation import ConvergenceError, InvalidInputError, broadcast_inputs

Kind = Literal["call", "put"]

_INPUT_NAMES = ("spot", "strike", "vol", "rate", "expiry")
_GREEK_NAMES = ("delta", "gamma", "vega", "theta", "rho")


def _check_kind(kind: str) -> None:
    if kind not in ("call", "put"):
        raise InvalidInputError(f"kind must be 'call' or 'put', got {kind!r}")


def price(spot, strike, vol, rate, expiry, kind: Kind = "call") -> float | np.ndarray:
    """European option price(s). Scalar in → float out; any array in → ndarray out.

    >>> price(100, 100, 0.2, 0.05, 1.0, "call")  # doctest: +ELLIPSIS
    10.45...
    """
    _check_kind(kind)
    scalar_in = all(np.isscalar(x) for x in (spot, strike, vol, rate, expiry))
    if scalar_in:
        return float(
            backend.bs_price(
                float(spot), float(strike), float(vol), float(rate), float(expiry), kind
            )
        )
    s, k, v, r, t = broadcast_inputs(spot, strike, vol, rate, expiry, names=_INPUT_NAMES)
    return np.asarray(backend.bs_price_batch(s, k, v, r, t, kind))


def greeks(
    spot, strike, vol, rate, expiry, kind: Kind = "call"
) -> dict[str, float] | dict[str, np.ndarray]:
    """Delta/gamma/vega/theta/rho. Scalars → dict of floats; arrays (broadcast
    together) → dict of ndarrays.

    At expiry==0 or vol==0 the almost-everywhere limit values are returned
    (delta = ITM indicator; gamma and vega collapse to 0).
    """
    _check_kind(kind)
    scalar_in = all(np.isscalar(x) for x in (spot, strike, vol, rate, expiry))
    if scalar_in:
        return dict(
            backend.bs_greeks(
                float(spot), float(strike), float(vol), float(rate), float(expiry), kind
            )
        )
    s, k, v, r, t = broadcast_inputs(spot, strike, vol, rate, expiry, names=_INPUT_NAMES)
    raw = backend.bs_greeks_batch(s, k, v, r, t, kind)
    return {name: np.asarray(raw[name]) for name in _GREEK_NAMES}


def implied_vol(
    price: float, spot: float, strike: float, rate: float, expiry: float, kind: Kind = "call"
) -> float:
    """Implied volatility from an observed price (Newton, bisection-safeguarded).

    Raises InvalidInputError when the price is outside the attainable range
    (e.g. below intrinsic value) and ConvergenceError if iteration fails —
    never returns NaN.
    """
    _check_kind(kind)
    try:
        return float(
            backend.bs_implied_vol(
                float(price), float(spot), float(strike), float(rate), float(expiry), kind
            )
        )
    except ValueError as exc:
        raise InvalidInputError(str(exc)) from exc
    except RuntimeError as exc:
        raise ConvergenceError(str(exc)) from exc
