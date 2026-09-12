"""Pure-Python/NumPy reference backend mirroring the `_qfcore` C++ API.

Must stay signature- and error-compatible with cpp/bindings/bindings.cpp —
the parity test suite (tests/python) asserts both backends agree to 1e-12
on all deterministic functions.

Backend error contract (same as the native module):
    ValueError    — invalid inputs (domain errors)
    RuntimeError  — a numerical routine failed to converge
"""

from __future__ import annotations

import numpy as np
from scipy import optimize, stats


def _validate(spot: float, strike: float, vol: float, expiry: float) -> None:
    if not (spot > 0 and np.isfinite(spot)):
        raise ValueError("spot must be > 0 and finite")
    if not (strike > 0 and np.isfinite(strike)):
        raise ValueError("strike must be > 0 and finite")
    if vol < 0 or not np.isfinite(vol):
        raise ValueError("vol must be >= 0 and finite")
    if expiry < 0 or not np.isfinite(expiry):
        raise ValueError("expiry must be >= 0 and finite")


def _parse_kind(kind: str) -> float:
    """Returns +1.0 for call, -1.0 for put."""
    if kind == "call":
        return 1.0
    if kind == "put":
        return -1.0
    raise ValueError("kind must be 'call' or 'put'")


def _payoff(s, strike: float, sign: float):
    return np.maximum(sign * (s - strike), 0.0)


# --- Black-Scholes -------------------------------------------------------------


def bs_price(
    spot: float, strike: float, vol: float, rate: float, expiry: float, kind: str
) -> float:
    sign = _parse_kind(kind)
    _validate(spot, strike, vol, expiry)
    if expiry == 0.0 or vol == 0.0:
        fwd = spot * np.exp(rate * expiry)
        df = np.exp(-rate * expiry)
        return float(df * _payoff(fwd, strike, sign))
    sqrt_t = np.sqrt(expiry)
    d1 = (np.log(spot / strike) + (rate + 0.5 * vol**2) * expiry) / (vol * sqrt_t)
    d2 = d1 - vol * sqrt_t
    df = np.exp(-rate * expiry)
    if sign > 0:
        return float(spot * stats.norm.cdf(d1) - strike * df * stats.norm.cdf(d2))
    return float(strike * df * stats.norm.cdf(-d2) - spot * stats.norm.cdf(-d1))


def bs_price_batch(spot, strike, vol, rate, expiry, kind: str) -> np.ndarray:
    arrays = [np.asarray(a, dtype=np.float64).ravel() for a in (spot, strike, vol, rate, expiry)]
    n = arrays[0].size
    if any(a.size != n for a in arrays):
        raise ValueError("all input arrays must have equal length (broadcast in Python)")
    out = np.empty(n, dtype=np.float64)
    for i in range(n):  # scalar loop by design — reference implementation
        out[i] = bs_price(
            arrays[0][i], arrays[1][i], arrays[2][i], arrays[3][i], arrays[4][i], kind
        )
    return out


def bs_greeks(spot, strike, vol, rate, expiry, kind: str) -> dict:
    sign = _parse_kind(kind)
    _validate(spot, strike, vol, expiry)

    if expiry == 0.0 or vol == 0.0:
        # Almost-everywhere limits: N(d1), N(d2) -> ITM indicator, phi(d1) -> 0.
        fwd = spot * np.exp(rate * expiry)
        df = np.exp(-rate * expiry)
        ind = 1.0 if fwd > strike else (0.0 if fwd < strike else 0.5)
        if sign > 0:
            return {
                "delta": ind,
                "gamma": 0.0,
                "vega": 0.0,
                "theta": float(-rate * strike * df * ind),
                "rho": float(strike * expiry * df * ind),
            }
        return {
            "delta": ind - 1.0,
            "gamma": 0.0,
            "vega": 0.0,
            "theta": float(rate * strike * df * (1.0 - ind)),
            "rho": float(-strike * expiry * df * (1.0 - ind)),
        }

    sqrt_t = np.sqrt(expiry)
    d1 = (np.log(spot / strike) + (rate + 0.5 * vol**2) * expiry) / (vol * sqrt_t)
    d2 = d1 - vol * sqrt_t
    df = np.exp(-rate * expiry)
    pdf_d1 = stats.norm.pdf(d1)
    gamma = pdf_d1 / (spot * vol * sqrt_t)
    vega = spot * pdf_d1 * sqrt_t
    if sign > 0:
        delta = stats.norm.cdf(d1)
        theta = -spot * pdf_d1 * vol / (2 * sqrt_t) - rate * strike * df * stats.norm.cdf(d2)
        rho = strike * expiry * df * stats.norm.cdf(d2)
    else:
        delta = stats.norm.cdf(d1) - 1.0
        theta = -spot * pdf_d1 * vol / (2 * sqrt_t) + rate * strike * df * stats.norm.cdf(-d2)
        rho = -strike * expiry * df * stats.norm.cdf(-d2)
    return {
        "delta": float(delta),
        "gamma": float(gamma),
        "vega": float(vega),
        "theta": float(theta),
        "rho": float(rho),
    }


def bs_greeks_batch(spot, strike, vol, rate, expiry, kind: str) -> dict:
    arrays = [np.asarray(a, dtype=np.float64).ravel() for a in (spot, strike, vol, rate, expiry)]
    n = arrays[0].size
    if any(a.size != n for a in arrays):
        raise ValueError("all input arrays must have equal length (broadcast in Python)")
    out = {
        name: np.empty(n, dtype=np.float64) for name in ("delta", "gamma", "vega", "theta", "rho")
    }
    for i in range(n):
        g = bs_greeks(arrays[0][i], arrays[1][i], arrays[2][i], arrays[3][i], arrays[4][i], kind)
        for name in out:
            out[name][i] = g[name]
    return out


def bs_implied_vol(
    price, spot, strike, rate, expiry, kind: str, lo: float = 1e-6, hi: float = 5.0
) -> float:
    _parse_kind(kind)
    _validate(spot, strike, lo, expiry)
    if not (price > 0 and np.isfinite(price)):
        raise ValueError("price must be > 0 and finite")
    if expiry == 0.0:
        raise ValueError("implied vol undefined at expiry == 0")

    def objective(v: float) -> float:
        return bs_price(spot, strike, v, rate, expiry, kind) - price

    # Boundary comparison carries a small tolerance: deep-ITM prices can round
    # a few ulps below intrinsic and must not be rejected (mirrors the C++ core).
    tol = 1e-12 * max(1.0, price)
    f_lo, f_hi = objective(lo), objective(hi)
    if f_lo > tol or f_hi < -tol:
        raise ValueError("implied vol: target price outside attainable range [lo, hi]")
    if f_lo >= 0.0:
        return float(lo)  # price sits on the vol-floor boundary (within noise)
    if f_hi <= 0.0:
        return float(hi)  # price sits on the vol-ceiling boundary
    try:
        return float(optimize.brentq(objective, lo, hi, xtol=1e-14, maxiter=200))
    except RuntimeError as exc:  # brentq non-convergence
        raise RuntimeError(f"implied vol: no convergence after 200 iterations ({exc})") from exc


# --- American (CRR binomial) -----------------------------------------------------


def binomial_american(spot, strike, vol, rate, expiry, kind: str, n_steps: int = 1024) -> float:
    sign = _parse_kind(kind)
    _validate(spot, strike, vol, expiry)
    if n_steps <= 0:
        raise ValueError("n_steps must be > 0")

    intrinsic_now = max(sign * (spot - strike), 0.0)
    if expiry == 0.0:
        return float(intrinsic_now)
    if vol == 0.0:
        df = np.exp(-rate * expiry)
        fwd = spot * np.exp(rate * expiry)
        european = df * max(sign * (fwd - strike), 0.0)
        return float(max(intrinsic_now, european))

    dt = expiry / n_steps
    u = np.exp(vol * np.sqrt(dt))
    d = 1.0 / u
    growth = np.exp(rate * dt)
    p = (growth - d) / (u - d)
    if not 0.0 < p < 1.0:
        raise ValueError(
            "binomial lattice unstable for these parameters: increase n_steps "
            "(need vol > |rate|*sqrt(T/n_steps))"
        )
    disc = 1.0 / growth

    j = np.arange(n_steps + 1, dtype=np.float64)
    st = spot * u ** (2.0 * j - n_steps)
    values = np.maximum(sign * (st - strike), 0.0)
    for step in range(n_steps - 1, -1, -1):
        values = disc * (p * values[1:] + (1.0 - p) * values[:-1])
        st = spot * u ** (2.0 * np.arange(step + 1, dtype=np.float64) - step)
        values = np.maximum(values, np.maximum(sign * (st - strike), 0.0))
    return float(values[0])


# --- Monte Carlo -----------------------------------------------------------------


def _validate_mc(spot, strike, vol, expiry, n_paths: int) -> None:
    _validate(spot, strike, vol, expiry)
    if n_paths <= 0:
        raise ValueError("n_paths must be > 0")


def mc_price_european(
    spot,
    strike,
    vol,
    rate,
    expiry,
    kind: str,
    n_paths: int = 100_000,
    seed: int = 42,
    antithetic: bool = True,
) -> dict:
    sign = _parse_kind(kind)
    _validate_mc(spot, strike, vol, expiry, n_paths)
    rng = np.random.default_rng(seed)
    z = rng.standard_normal(n_paths)
    drift = (rate - 0.5 * vol**2) * expiry
    diffusion = vol * np.sqrt(expiry)
    df = np.exp(-rate * expiry)
    vega_coeff = (rate + 0.5 * vol**2) * expiry

    def leg(zz: np.ndarray) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
        st = spot * np.exp(drift + diffusion * zz)
        payoff = _payoff(st, strike, sign)
        itm = payoff > 0.0
        delta = np.where(itm, sign * st / spot, 0.0)
        if vol > 0.0:
            vega = np.where(itm, sign * st * (np.log(st / spot) - vega_coeff) / vol, 0.0)
        else:
            vega = np.zeros_like(st)
        return payoff, delta, vega

    payoff, delta, vega = leg(z)
    if antithetic:
        p2, d2, v2 = leg(-z)
        payoff = 0.5 * (payoff + p2)
        delta = 0.5 * (delta + d2)
        vega = 0.5 * (vega + v2)

    return {
        "price": float(df * payoff.mean()),
        "std_error": float(df * payoff.std(ddof=0) / np.sqrt(n_paths)),
        "delta": float(df * delta.mean()),
        "vega": float(df * vega.mean()),
        "n_paths": int(n_paths),
    }


def _simulate_path_stats(
    spot, vol, rate, expiry, n_steps: int, n_paths: int, seed: int, antithetic: bool
):
    """Step through GBM paths, yielding per-leg (avg, terminal, min, max) arrays.

    Streams over steps (never materialises the paths matrix) so memory is
    O(n_paths), not O(n_paths * n_steps).
    """
    rng = np.random.default_rng(seed)
    dt = expiry / n_steps
    drift = (rate - 0.5 * vol**2) * dt
    diffusion = vol * np.sqrt(dt)
    legs = 2 if antithetic else 1
    flips = (1.0, -1.0)[:legs]

    st = [np.full(n_paths, float(spot)) for _ in flips]
    run_sum = [np.zeros(n_paths) for _ in flips]
    run_min = [np.full(n_paths, float(spot)) for _ in flips]
    run_max = [np.full(n_paths, float(spot)) for _ in flips]

    for _ in range(n_steps):
        z = rng.standard_normal(n_paths)
        for leg_idx, flip in enumerate(flips):
            st[leg_idx] = st[leg_idx] * np.exp(drift + diffusion * flip * z)
            run_sum[leg_idx] += st[leg_idx]
            np.minimum(run_min[leg_idx], st[leg_idx], out=run_min[leg_idx])
            np.maximum(run_max[leg_idx], st[leg_idx], out=run_max[leg_idx])

    return [(run_sum[i] / n_steps, st[i], run_min[i], run_max[i]) for i in range(legs)]


def mc_price_asian(
    spot,
    strike,
    vol,
    rate,
    expiry,
    kind: str,
    n_steps: int = 252,
    n_paths: int = 100_000,
    seed: int = 42,
    antithetic: bool = True,
) -> dict:
    sign = _parse_kind(kind)
    _validate_mc(spot, strike, vol, expiry, n_paths)
    if n_steps <= 0:
        raise ValueError("n_steps must be > 0")
    if expiry == 0.0:
        return {
            "price": float(_payoff(spot, strike, sign)),
            "std_error": 0.0,
            "n_paths": int(n_paths),
        }

    legs = _simulate_path_stats(spot, vol, rate, expiry, n_steps, n_paths, seed, antithetic)
    payoff = np.mean([_payoff(avg, strike, sign) for avg, _, _, _ in legs], axis=0)
    df = np.exp(-rate * expiry)
    return {
        "price": float(df * payoff.mean()),
        "std_error": float(df * payoff.std(ddof=0) / np.sqrt(n_paths)),
        "n_paths": int(n_paths),
    }


def mc_price_barrier(
    spot,
    strike,
    vol,
    rate,
    expiry,
    kind: str,
    barrier: float,
    barrier_type: str,
    n_steps: int = 252,
    n_paths: int = 100_000,
    seed: int = 42,
    antithetic: bool = True,
) -> dict:
    sign = _parse_kind(kind)
    _validate_mc(spot, strike, vol, expiry, n_paths)
    if n_steps <= 0:
        raise ValueError("n_steps must be > 0")
    if not (barrier > 0 and np.isfinite(barrier)):
        raise ValueError("barrier must be > 0 and finite")
    if barrier_type not in ("up-out", "down-out", "up-in", "down-in"):
        raise ValueError("barrier_type must be one of 'up-out', 'down-out', 'up-in', 'down-in'")

    up = barrier_type.startswith("up")
    knock_out = barrier_type.endswith("out")

    legs = _simulate_path_stats(spot, vol, rate, expiry, n_steps, n_paths, seed, antithetic)
    leg_payoffs = []
    for _, terminal, path_min, path_max in legs:
        crossed = path_max >= barrier if up else path_min <= barrier
        alive = ~crossed if knock_out else crossed
        leg_payoffs.append(np.where(alive, _payoff(terminal, strike, sign), 0.0))
    payoff = np.mean(leg_payoffs, axis=0)
    df = np.exp(-rate * expiry)
    return {
        "price": float(df * payoff.mean()),
        "std_error": float(df * payoff.std(ddof=0) / np.sqrt(n_paths)),
        "n_paths": int(n_paths),
    }


# --- Risk --------------------------------------------------------------------------


def historical_var(returns, confidence: float = 0.99) -> dict:
    r = np.asarray(returns, dtype=np.float64).ravel()
    if r.size < 2:
        raise ValueError("need at least 2 returns")
    if not 0.0 < confidence < 1.0:
        raise ValueError("confidence must be in (0, 1)")
    quantile = float(np.quantile(r, 1.0 - confidence))  # linear interpolation
    tail = r[r <= quantile]
    return {
        "var": -quantile,
        "expected_shortfall": float(-tail.mean()) if tail.size else -quantile,
    }


def parametric_var(returns, confidence: float = 0.99) -> dict:
    r = np.asarray(returns, dtype=np.float64).ravel()
    if r.size < 2:
        raise ValueError("need at least 2 returns")
    if not 0.0 < confidence < 1.0:
        raise ValueError("confidence must be in (0, 1)")
    mean, stddev = float(r.mean()), float(r.std(ddof=1))
    z = float(stats.norm.ppf(1.0 - confidence))
    return {
        "var": -(mean + z * stddev),
        "expected_shortfall": -(mean - stddev * float(stats.norm.pdf(z)) / (1.0 - confidence)),
    }
