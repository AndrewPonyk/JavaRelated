"""American option pricing (Cox-Ross-Rubinstein binomial lattice)."""

from __future__ import annotations

from typing import Literal

from quantfinlib._compat import backend
from quantfinlib.utils.validation import InvalidInputError

Kind = Literal["call", "put"]

MAX_STEPS = 100_000  # lattice is O(n^2) time, O(n) space


def price_american(
    spot: float,
    strike: float,
    vol: float,
    rate: float,
    expiry: float,
    kind: Kind = "call",
    *,
    n_steps: int = 1024,
) -> float:
    """American option price on a CRR lattice (no dividends).

    Convergence is O(1/n_steps); the default 1024 steps prices to ~1e-3 on
    typical inputs. Without dividends an American call equals the European
    call; the put carries an early-exercise premium.
    """
    if kind not in ("call", "put"):
        raise InvalidInputError(f"kind must be 'call' or 'put', got {kind!r}")
    if not 0 < n_steps <= MAX_STEPS:
        raise InvalidInputError(f"n_steps must be in (0, {MAX_STEPS}], got {n_steps}")
    try:
        return float(
            backend.binomial_american(
                float(spot),
                float(strike),
                float(vol),
                float(rate),
                float(expiry),
                kind,
                n_steps=int(n_steps),
            )
        )
    except ValueError as exc:
        raise InvalidInputError(str(exc)) from exc
