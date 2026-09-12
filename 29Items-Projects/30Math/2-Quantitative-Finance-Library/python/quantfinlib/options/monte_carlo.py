"""Monte Carlo pricing façade over the qfcore engine.

All estimators are deterministic for a given seed (record the seed alongside
any published number — it is what makes the result reproducible).
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

from quantfinlib._compat import backend
from quantfinlib.utils.validation import InvalidInputError

Kind = Literal["call", "put"]
BarrierKind = Literal["up-out", "down-out", "up-in", "down-in"]

# Guard the C++ boundary: n_paths (x n_steps) drives allocations in native code.
MAX_PATHS = 100_000_000
MAX_STEPS = 100_000


def _call_backend(fn, /, *args, **kwargs):
    """Backend domain errors surface as the library's InvalidInputError."""
    try:
        return fn(*args, **kwargs)
    except ValueError as exc:
        raise InvalidInputError(str(exc)) from exc


@dataclass(frozen=True)
class McPrice:
    price: float
    std_error: float
    n_paths: int
    delta: float | None = None  # pathwise estimator (European only)
    vega: float | None = None  # pathwise estimator (European only)

    def confidence_interval(self, k: float = 1.96) -> tuple[float, float]:
        return (self.price - k * self.std_error, self.price + k * self.std_error)


def _check_common(kind: str, n_paths: int, n_steps: int | None = None) -> None:
    if kind not in ("call", "put"):
        raise InvalidInputError(f"kind must be 'call' or 'put', got {kind!r}")
    if not 0 < n_paths <= MAX_PATHS:
        raise InvalidInputError(f"n_paths must be in (0, {MAX_PATHS}], got {n_paths}")
    if n_steps is not None and not 0 < n_steps <= MAX_STEPS:
        raise InvalidInputError(f"n_steps must be in (0, {MAX_STEPS}], got {n_steps}")


def price_european(
    spot: float,
    strike: float,
    vol: float,
    rate: float,
    expiry: float,
    kind: Kind = "call",
    *,
    n_paths: int = 100_000,
    seed: int = 42,
    antithetic: bool = True,
) -> McPrice:
    """European option under GBM (exact terminal-value scheme, no time-step bias).

    The result carries pathwise delta/vega estimators computed from the same
    simulation at negligible extra cost.
    """
    _check_common(kind, n_paths)
    raw = _call_backend(
        backend.mc_price_european,
        float(spot),
        float(strike),
        float(vol),
        float(rate),
        float(expiry),
        kind,
        n_paths=int(n_paths),
        seed=int(seed),
        antithetic=bool(antithetic),
    )
    return McPrice(
        price=raw["price"],
        std_error=raw["std_error"],
        n_paths=raw["n_paths"],
        delta=raw["delta"],
        vega=raw["vega"],
    )


def price_asian(
    spot: float,
    strike: float,
    vol: float,
    rate: float,
    expiry: float,
    kind: Kind = "call",
    *,
    n_steps: int = 252,
    n_paths: int = 100_000,
    seed: int = 42,
    antithetic: bool = True,
) -> McPrice:
    """Arithmetic-average Asian option; average monitored at n_steps equally
    spaced dates in (0, T]."""
    _check_common(kind, n_paths, n_steps)
    raw = _call_backend(
        backend.mc_price_asian,
        float(spot),
        float(strike),
        float(vol),
        float(rate),
        float(expiry),
        kind,
        n_steps=int(n_steps),
        n_paths=int(n_paths),
        seed=int(seed),
        antithetic=bool(antithetic),
    )
    return McPrice(price=raw["price"], std_error=raw["std_error"], n_paths=raw["n_paths"])


def price_barrier(
    spot: float,
    strike: float,
    vol: float,
    rate: float,
    expiry: float,
    kind: Kind = "call",
    *,
    barrier: float,
    barrier_type: BarrierKind,
    n_steps: int = 252,
    n_paths: int = 100_000,
    seed: int = 42,
    antithetic: bool = True,
) -> McPrice:
    """Discretely monitored barrier option (inception spot counts as a
    monitoring date). In-out parity holds exactly per path: for identical
    seeds, knock-in + knock-out == vanilla."""
    _check_common(kind, n_paths, n_steps)
    if barrier_type not in ("up-out", "down-out", "up-in", "down-in"):
        raise InvalidInputError(
            f"barrier_type must be one of 'up-out', 'down-out', 'up-in', 'down-in', "
            f"got {barrier_type!r}"
        )
    raw = _call_backend(
        backend.mc_price_barrier,
        float(spot),
        float(strike),
        float(vol),
        float(rate),
        float(expiry),
        kind,
        float(barrier),
        barrier_type,
        n_steps=int(n_steps),
        n_paths=int(n_paths),
        seed=int(seed),
        antithetic=bool(antithetic),
    )
    return McPrice(price=raw["price"], std_error=raw["std_error"], n_paths=raw["n_paths"])
