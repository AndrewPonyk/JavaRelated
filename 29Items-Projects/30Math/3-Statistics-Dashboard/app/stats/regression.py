"""Regression analysis via statsmodels (lazy import — keep light envs importable).

Column names are sanitized before entering patsy formulas: patsy evaluates
formula terms as Python-ish expressions, so raw user text must never be
concatenated into one (TECH-NOTES §3.6.17).
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field

import numpy as np
import pandas as pd
from scipy import stats as scipy_stats

from app.core.errors import AnalysisError

_IDENTIFIER = re.compile(r"[A-Za-z_]\w*\Z")


@dataclass(frozen=True)
class RegressionResult:
    model_kind: str  # "ols" | "logistic"
    formula: str
    n_obs: int
    coefficients: pd.DataFrame  # term, coef, std_err, stat, p_value, ci_low, ci_high
    r_squared: float | None  # OLS
    adj_r_squared: float | None  # OLS
    pseudo_r_squared: float | None  # logistic
    fitted: np.ndarray = field(default_factory=lambda: np.array([]))
    residuals: np.ndarray = field(default_factory=lambda: np.array([]))
    #: durbin_watson / breusch_pagan_p / jarque_bera_p (OLS); log_likelihood (logistic)
    diagnostics: dict[str, float] = field(default_factory=dict)
    vif: pd.DataFrame | None = None  # term, vif — multicollinearity screen

    def to_json_dict(self) -> dict[str, object]:
        """Serializable form persisted into analysis_runs.results_json (arrays excluded)."""
        return {
            "model_kind": self.model_kind,
            "formula": self.formula,
            "n_obs": self.n_obs,
            "coefficients": self.coefficients.to_dict(orient="records"),
            "r_squared": self.r_squared,
            "adj_r_squared": self.adj_r_squared,
            "pseudo_r_squared": self.pseudo_r_squared,
            "diagnostics": self.diagnostics,
            "vif": self.vif.to_dict(orient="records") if self.vif is not None else None,
        }


def _formula_term(name: str) -> str:
    """Quote non-identifier column names with patsy's Q(); reject injection-y ones."""
    if _IDENTIFIER.fullmatch(name):
        return name
    if '"' in name or "\\" in name or "\n" in name:
        raise AnalysisError(
            f"unsafe column name: {name!r}",
            user_message=f"Column name {name!r} contains characters that cannot be used here.",
        )
    return f'Q("{name}")'


def _import_statsmodels():
    try:
        import statsmodels.api as sm
        import statsmodels.formula.api as smf

        return sm, smf
    except ImportError as exc:  # pragma: no cover
        raise AnalysisError(
            "statsmodels is not installed",
            user_message="Regression requires the statsmodels package.",
        ) from exc


def _coefficient_table(fitted) -> pd.DataFrame:  # — statsmodels result type
    ci = fitted.conf_int()
    return pd.DataFrame(
        {
            "term": fitted.params.index,
            "coef": fitted.params.to_numpy(),
            "std_err": fitted.bse.to_numpy(),
            "stat": fitted.tvalues.to_numpy(),
            "p_value": fitted.pvalues.to_numpy(),
            "ci_low": ci[0].to_numpy(),
            "ci_high": ci[1].to_numpy(),
        }
    ).reset_index(drop=True)


def _build_formula(outcome: str, features: list[str]) -> str:
    if not features:
        raise AnalysisError("no features", user_message="Pick at least one feature column.")
    return f"{_formula_term(outcome)} ~ " + " + ".join(_formula_term(f) for f in features)


def _compute_vif(fitted) -> pd.DataFrame | None:
    """VIF per non-intercept term (> ~5 flags multicollinearity). None when < 2 terms."""
    from statsmodels.stats.outliers_influence import variance_inflation_factor

    exog = np.asarray(fitted.model.exog, dtype=float)
    names = list(fitted.model.exog_names)
    terms = [(i, name) for i, name in enumerate(names) if name != "Intercept"]
    if len(terms) < 2:
        return None  # VIF is meaningless with a single predictor
    rows = [{"term": name, "vif": float(variance_inflation_factor(exog, i))} for i, name in terms]
    return pd.DataFrame(rows)


def fit_ols(df: pd.DataFrame, outcome: str, features: list[str]) -> RegressionResult:
    """Ordinary least squares with residual diagnostics and a VIF screen."""
    _, smf = _import_statsmodels()
    from statsmodels.stats.diagnostic import het_breuschpagan
    from statsmodels.stats.stattools import durbin_watson

    formula = _build_formula(outcome, features)
    data = df[[outcome, *features]].dropna()
    if len(data) <= len(features) + 1:
        raise AnalysisError(
            "not enough rows for OLS",
            user_message="Not enough complete rows to fit this model.",
        )
    fitted = smf.ols(formula, data=data).fit()

    residuals = np.asarray(fitted.resid, dtype=float)
    diagnostics: dict[str, float] = {
        "durbin_watson": float(durbin_watson(residuals)),
        "jarque_bera_p": float(scipy_stats.jarque_bera(residuals).pvalue),
    }
    try:
        _, bp_p, _, _ = het_breuschpagan(residuals, fitted.model.exog)
        diagnostics["breusch_pagan_p"] = float(bp_p)
    except Exception:  # singular exog etc. — heteroskedasticity screen is best-effort
        pass

    return RegressionResult(
        model_kind="ols",
        formula=formula,
        n_obs=int(fitted.nobs),
        coefficients=_coefficient_table(fitted),
        r_squared=float(fitted.rsquared),
        adj_r_squared=float(fitted.rsquared_adj),
        pseudo_r_squared=None,
        fitted=np.asarray(fitted.fittedvalues, dtype=float),
        residuals=residuals,
        diagnostics=diagnostics,
        vif=_compute_vif(fitted),
    )


def fit_logistic(df: pd.DataFrame, outcome: str, features: list[str]) -> RegressionResult:
    """Logistic regression for a binary (0/1) outcome."""
    _, smf = _import_statsmodels()

    formula = _build_formula(outcome, features)
    data = df[[outcome, *features]].dropna()
    if set(pd.unique(data[outcome])) - {0, 1, 0.0, 1.0, True, False}:
        raise AnalysisError(
            "outcome is not 0/1",
            user_message=f"'{outcome}' must contain only 0/1 values for logistic regression.",
        )
    try:
        fitted = smf.logit(formula, data=data).fit(disp=False)
    except Exception as exc:
        raise AnalysisError(
            f"logit failed to converge: {exc}",
            user_message=(
                "The logistic model failed to converge — check for perfectly separating "
                "features or collinear columns."
            ),
        ) from exc

    response = np.asarray(fitted.model.endog, dtype=float)
    predicted = np.asarray(fitted.predict(), dtype=float)
    return RegressionResult(
        model_kind="logistic",
        formula=formula,
        n_obs=int(fitted.nobs),
        coefficients=_coefficient_table(fitted),
        r_squared=None,
        adj_r_squared=None,
        pseudo_r_squared=float(fitted.prsquared),
        fitted=predicted,
        residuals=response - predicted,  # response residuals — for calibration plots
        diagnostics={"log_likelihood": float(fitted.llf)},
        vif=_compute_vif(fitted),
    )


def qq_points(residuals: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """(theoretical normal quantiles, ordered residuals) for a Q-Q plot."""
    arr = np.asarray(residuals, dtype=float)
    arr = arr[~np.isnan(arr)]
    if arr.size < 3:
        raise AnalysisError(
            "too few residuals for a Q-Q plot",
            user_message="Not enough residuals to draw a Q-Q plot.",
        )
    (theoretical, ordered), _ = scipy_stats.probplot(arr, dist="norm")
    return np.asarray(theoretical), np.asarray(ordered)
