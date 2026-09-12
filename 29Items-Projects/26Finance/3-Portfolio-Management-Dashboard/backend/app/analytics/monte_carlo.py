"""Monte Carlo risk engine: correlated multi-asset Geometric Brownian Motion.

Simulates ``n_sims`` forward paths of a portfolio over ``n_days`` using
correlated GBM. Asset shocks are correlated via the Cholesky factor of the
(annualized) covariance matrix, so cross-asset dependence is preserved.

The whole simulation is vectorized into a single ``(n_sims, n_days, n_assets)``
normal draw — no per-path Python loops — so 50k paths over a year run in well
under a second for a handful of assets.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from .returns import TRADING_DAYS_PER_YEAR


@dataclass
class SimulationResult:
    """Down-sampled, API-friendly result of a Monte Carlo run.

    Raw paths are intentionally *not* returned by default — only percentile
    bands over time plus terminal statistics — to keep response payloads small.
    """

    horizon_days: int
    n_sims: int
    initial_value: float
    expected_terminal_value: float
    median_terminal_value: float
    prob_loss: float
    var: float
    cvar: float
    var_level: float
    time_axis: list[int]
    band_p5: list[float]
    band_p50: list[float]
    band_p95: list[float]

    def as_dict(self) -> dict:
        return {
            "horizon_days": self.horizon_days,
            "n_sims": self.n_sims,
            "initial_value": self.initial_value,
            "expected_terminal_value": self.expected_terminal_value,
            "median_terminal_value": self.median_terminal_value,
            "prob_loss": self.prob_loss,
            "var": self.var,
            "cvar": self.cvar,
            "var_level": self.var_level,
            "bands": {
                "t": self.time_axis,
                "p5": self.band_p5,
                "p50": self.band_p50,
                "p95": self.band_p95,
            },
        }


def simulate_portfolio(
    mu: np.ndarray,
    cov: np.ndarray,
    weights: np.ndarray,
    n_days: int = TRADING_DAYS_PER_YEAR,
    n_sims: int = 10_000,
    initial_value: float = 1.0,
    var_level: float = 0.95,
    periods_per_year: int = TRADING_DAYS_PER_YEAR,
    seed: int | None = None,
) -> SimulationResult:
    """Run a correlated-GBM Monte Carlo simulation of portfolio value.

    Args:
        mu: Annualized expected returns, shape (N,).
        cov: Annualized covariance matrix, shape (N, N).
        weights: Target portfolio weights (rebalanced each step), shape (N,).
        n_days: Simulation horizon in trading days.
        n_sims: Number of simulated paths.
        initial_value: Starting portfolio value.
        var_level: Confidence level for horizon VaR/CVaR.
        seed: RNG seed for reproducibility.
    """
    mu = np.asarray(mu, dtype=float)
    cov = np.asarray(cov, dtype=float)
    weights = np.asarray(weights, dtype=float)
    n_assets = mu.shape[0]
    if cov.shape != (n_assets, n_assets):
        raise ValueError("cov must be (N, N) matching mu")
    if weights.shape[0] != n_assets:
        raise ValueError("weights must match number of assets")

    dt = 1.0 / periods_per_year
    rng = np.random.default_rng(seed)

    # Cholesky factor; add tiny jitter if the matrix is only borderline PSD.
    try:
        chol = np.linalg.cholesky(cov)
    except np.linalg.LinAlgError:  # pragma: no cover - defensive
        chol = np.linalg.cholesky(cov + np.eye(n_assets) * 1e-10)

    # (n_sims, n_days, n_assets) standard normals -> correlated shocks.
    z = rng.standard_normal((n_sims, n_days, n_assets))
    correlated = z @ chol.T * np.sqrt(dt)

    # Per-step log returns under GBM, then convert to simple returns.
    drift = (mu - 0.5 * np.diag(cov)) * dt
    log_returns = drift + correlated
    asset_simple_returns = np.exp(log_returns) - 1.0

    # Daily-rebalanced portfolio step returns -> value paths.
    port_step_returns = asset_simple_returns @ weights  # (n_sims, n_days)
    growth = np.cumprod(1.0 + port_step_returns, axis=1)
    start = np.ones((n_sims, 1))
    values = initial_value * np.concatenate([start, growth], axis=1)

    terminal = values[:, -1]
    horizon_return = terminal / initial_value - 1.0

    alpha = 1.0 - var_level
    var_quantile = np.percentile(horizon_return, alpha * 100.0)
    tail = horizon_return[horizon_return <= var_quantile]
    var = float(-var_quantile)
    cvar = float(-tail.mean()) if tail.size else var

    bands = np.percentile(values, [5, 50, 95], axis=0)

    return SimulationResult(
        horizon_days=n_days,
        n_sims=n_sims,
        initial_value=initial_value,
        expected_terminal_value=float(terminal.mean()),
        median_terminal_value=float(np.median(terminal)),
        prob_loss=float(np.mean(terminal < initial_value)),
        var=var,
        cvar=cvar,
        var_level=var_level,
        time_axis=list(range(n_days + 1)),
        band_p5=bands[0].tolist(),
        band_p50=bands[1].tolist(),
        band_p95=bands[2].tolist(),
    )
