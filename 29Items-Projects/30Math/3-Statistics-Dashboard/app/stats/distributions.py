"""Distribution fitting: MLE per candidate, ranked by AIC; KS statistic as a diagnostic.

Caveat baked into the UI copy: KS p-values are biased when parameters were
estimated from the same sample — treat them as *relative* diagnostics only.
"""

from __future__ import annotations

import warnings
from dataclasses import dataclass

import numpy as np
from scipy import stats

from app.core.errors import AnalysisError

MIN_OBSERVATIONS = 20

CANDIDATES: dict[str, stats.rv_continuous] = {
    "normal": stats.norm,
    "lognormal": stats.lognorm,
    "exponential": stats.expon,
    "gamma": stats.gamma,
    "weibull": stats.weibull_min,
    "beta": stats.beta,
}

#: Fits for these are skipped when the sample contains non-positive values.
_POSITIVE_SUPPORT = frozenset({"lognormal", "exponential", "gamma", "weibull"})

#: Beta only applies to samples strictly inside (0, 1).
_UNIT_INTERVAL_SUPPORT = frozenset({"beta"})


def select_candidates(names: list[str]) -> dict[str, stats.rv_continuous]:
    """Subset of CANDIDATES by name (UI multiselect); unknown names raise."""
    unknown = [n for n in names if n not in CANDIDATES]
    if unknown:
        raise AnalysisError(
            f"unknown candidates: {unknown}",
            user_message=f"Unknown distribution(s): {', '.join(unknown)}.",
        )
    if not names:
        raise AnalysisError(
            "empty candidate list", user_message="Pick at least one candidate distribution."
        )
    return {name: CANDIDATES[name] for name in names}


@dataclass(frozen=True)
class FitResult:
    name: str
    params: tuple[float, ...]
    aic: float
    ks_statistic: float
    ks_p_value: float


def fit_distributions(
    x: np.ndarray, candidates: dict[str, stats.rv_continuous] | None = None
) -> list[FitResult]:
    """Fit every applicable candidate to x; return results sorted by AIC (best first)."""
    arr = np.asarray(x, dtype=float)
    arr = arr[~np.isnan(arr)]
    if arr.size < MIN_OBSERVATIONS:
        raise AnalysisError(
            f"n={arr.size} < {MIN_OBSERVATIONS}",
            user_message=f"Need at least {MIN_OBSERVATIONS} observations to fit distributions.",
        )
    if np.ptp(arr) == 0:
        raise AnalysisError(
            "constant sample",
            user_message="The column is constant — there is no distribution to fit.",
        )

    results: list[FitResult] = []
    for name, dist in (candidates or CANDIDATES).items():
        if name in _POSITIVE_SUPPORT and arr.min() <= 0:
            continue  # unsupported sample values for this candidate
        if name in _UNIT_INTERVAL_SUPPORT and (arr.min() <= 0 or arr.max() >= 1):
            continue  # beta requires values strictly inside (0, 1)
        try:
            # MLE on a mismatched candidate emits numeric warnings (sqrt of negatives
            # etc.) before the fit is rejected — noise, not signal; keep logs clean.
            with warnings.catch_warnings(), np.errstate(all="ignore"):
                warnings.simplefilter("ignore")
                params = dist.fit(arr)
                log_likelihood = float(np.sum(dist.logpdf(arr, *params)))
                if not np.isfinite(log_likelihood):
                    continue
                aic = 2.0 * len(params) - 2.0 * log_likelihood
                ks_stat, ks_p = stats.kstest(arr, dist.cdf, args=params)
            results.append(
                FitResult(
                    name=name,
                    params=tuple(float(p) for p in params),
                    aic=float(aic),
                    ks_statistic=float(ks_stat),
                    ks_p_value=float(ks_p),
                )
            )
        except Exception:  # — a failing candidate must not kill the ranking
            continue

    return sorted(results, key=lambda r: r.aic)


def pdf_curve(
    fit: FitResult, x_min: float, x_max: float, n_points: int = 200
) -> tuple[np.ndarray, np.ndarray]:
    """(x, pdf(x)) points for overlaying a fitted candidate on a histogram."""
    dist = CANDIDATES[fit.name]
    xs = np.linspace(x_min, x_max, n_points)
    return xs, dist.pdf(xs, *fit.params)


def qq_points(fit: FitResult, x: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """(theoretical quantiles of the fitted candidate, ordered sample) for a Q-Q plot."""
    arr = np.asarray(x, dtype=float)
    arr = np.sort(arr[~np.isnan(arr)])
    dist = CANDIDATES[fit.name]
    probabilities = (np.arange(1, arr.size + 1) - 0.5) / arr.size
    theoretical = dist.ppf(probabilities, *fit.params)
    return theoretical, arr
