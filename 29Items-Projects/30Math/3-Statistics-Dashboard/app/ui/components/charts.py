"""All Plotly figure builders — the single place chart style lives.

Method (dataviz skill): categorical slots in FIXED order (the ordering is the
CVD-safety mechanism — never cycle or repaint survivors when filters change),
one y-axis per figure, recessive hairline grid, legend whenever ≥ 2 series,
2px surface gaps between fills, thin marks, ≥8px point markers.

Palette validated 2026-07-11 (light surface #fcfcfb): ALL CHECKS PASS,
worst adjacent CVD ΔE 24.2. Aqua/yellow sit below 3:1 contrast on the light
surface → the relief rule applies: charts using them keep visible direct labels
or an accompanying table (the result card / st.dataframe provides it).

Dark mode is a *selection*, not an auto-flip: the dark tokens below are the
palette's own dark steps, chosen for the dark surface. The active set follows
the Streamlit theme base.

Beyond six groups color stops carrying identity: marks turn uniform (slot 1)
and position/labels take over — hues are never cycled or invented.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd
import plotly.graph_objects as go
from scipy import stats as scipy_stats

from app.stats.distributions import FitResult, pdf_curve

FONT = 'system-ui, -apple-system, "Segoe UI", sans-serif'

#: Beyond this many WebGL kicks in for scatter marks (TECH-NOTES §3.6.16).
_WEBGL_THRESHOLD = 10_000

#: Boxes stop drawing individual points above this group size (payload size).
_BOXPOINTS_MAX = 2_000

_MAX_COLORED_SERIES = 6


@dataclass(frozen=True)
class _Tokens:
    series: tuple[str, ...]
    surface: str
    grid: str
    baseline: str
    ink: str
    ink_muted: str
    diverging_mid: str


_LIGHT = _Tokens(
    series=("#2a78d6", "#1baf7a", "#eda100", "#008300", "#4a3aa7", "#e34948"),
    surface="#fcfcfb",
    grid="#e1e0d9",
    baseline="#c3c2b7",
    ink="#0b0b0b",
    ink_muted="#898781",
    diverging_mid="#f0efec",
)

_DARK = _Tokens(
    series=("#3987e5", "#199e70", "#c98500", "#008300", "#9085e9", "#e66767"),
    surface="#1a1a19",
    grid="#2c2c2a",
    baseline="#383835",
    ink="#ffffff",
    ink_muted="#898781",
    diverging_mid="#383835",
)


def _tokens() -> _Tokens:
    """Active token set — follows the Streamlit theme base (light outside Streamlit)."""
    try:
        import streamlit as st

        if st.get_option("theme.base") == "dark":
            return _DARK
    except Exception:
        pass
    return _LIGHT


def color_for(levels: list[str], level: str) -> str:
    """Entity-stable color: sorted levels map to slots, so a filter that removes a
    level never repaints the survivors (color follows the entity, not its rank)."""
    tokens = _tokens()
    ordered = sorted(levels, key=str)
    if len(ordered) > _MAX_COLORED_SERIES:
        return tokens.series[0]  # identity moves to position/labels beyond six groups
    return tokens.series[ordered.index(level)]


def _base_layout(fig: go.Figure, *, title: str, x_title: str = "", y_title: str = "") -> go.Figure:
    tokens = _tokens()
    fig.update_layout(
        title={"text": title, "x": 0.0, "font": {"size": 15, "color": tokens.ink}},
        font={"family": FONT, "size": 12, "color": tokens.ink},
        paper_bgcolor=tokens.surface,
        plot_bgcolor=tokens.surface,
        margin={"l": 48, "r": 16, "t": 64, "b": 40},
        legend={"orientation": "h", "yanchor": "bottom", "y": 1.0, "x": 0.0},
        hovermode="closest",
    )
    fig.update_xaxes(
        title_text=x_title,
        showgrid=False,
        linecolor=tokens.baseline,
        tickfont={"color": tokens.ink_muted},
    )
    fig.update_yaxes(
        title_text=y_title,
        gridcolor=tokens.grid,
        gridwidth=1,
        zerolinecolor=tokens.baseline,
        linecolor=tokens.baseline,
        tickfont={"color": tokens.ink_muted},
    )
    return fig


def group_comparison_fig(df: pd.DataFrame, outcome: str, group: str) -> go.Figure:
    """Distribution of a continuous outcome per group: outlined box + jittered points."""
    frame = df[[outcome, group]].dropna()
    levels = sorted(frame[group].astype(str).unique())
    many_groups = len(levels) > _MAX_COLORED_SERIES
    fig = go.Figure()
    for level in levels:
        values = frame.loc[frame[group].astype(str) == level, outcome]
        color = color_for(levels, level)
        fig.add_trace(
            go.Box(
                y=values,
                name=level,
                line={"width": 2, "color": color},
                fillcolor="rgba(0,0,0,0)",  # thin marks: outline only, no heavy fill
                marker={"color": color, "size": 8, "opacity": 0.45},
                boxpoints="all" if len(values) <= _BOXPOINTS_MAX else False,
                jitter=0.5,
                pointpos=0,
                showlegend=not many_groups,  # uniform color ⇒ the axis carries identity
                hovertemplate=f"{group}={level}<br>{outcome}=%{{y:.4g}}<extra></extra>",
            )
        )
    return _base_layout(fig, title=f"{outcome} by {group}", x_title=group, y_title=outcome)


def proportion_by_group_fig(df: pd.DataFrame, outcome: str, group: str) -> go.Figure:
    """Share of each outcome level per group (categorical/binary outcomes).

    Outcome levels are the colored series; beyond six, the smallest levels fold
    into "Other" instead of inventing hues.
    """
    tokens = _tokens()
    frame = df[[outcome, group]].dropna().astype({outcome: str, group: str})
    counts = frame[outcome].value_counts()
    if len(counts) > _MAX_COLORED_SERIES:
        keep = set(counts.index[: _MAX_COLORED_SERIES - 1])
        frame[outcome] = frame[outcome].where(frame[outcome].isin(keep), other="Other")
    shares = (
        frame.groupby([group, outcome])
        .size()
        .unstack(fill_value=0)
        .pipe(lambda t: t.div(t.sum(axis=1), axis=0))
    )
    outcome_levels = sorted(shares.columns)
    fig = go.Figure()
    for level in outcome_levels:
        fig.add_trace(
            go.Bar(
                x=list(shares.index),
                y=shares[level],
                name=str(level),
                marker={
                    "color": color_for(outcome_levels, level),
                    "line": {"color": tokens.surface, "width": 2},  # 2px surface gap
                },
                hovertemplate=f"{group}=%{{x}}<br>{outcome}={level}<br>share=%{{y:.1%}}<extra></extra>",
            )
        )
    fig.update_layout(barmode="group")
    fig.update_yaxes(tickformat=".0%", rangemode="tozero")
    return _base_layout(
        fig, title=f"{outcome} share by {group}", x_title=group, y_title=f"share of {outcome}"
    )


def histogram_with_fits_fig(
    x: np.ndarray, fits: list[FitResult], *, column: str, max_curves: int = 3
) -> go.Figure:
    """Density histogram (data = slot 1) + top fitted PDFs (slots 2+, 2px lines)."""
    tokens = _tokens()
    arr = np.asarray(x, dtype=float)
    arr = arr[~np.isnan(arr)]
    fig = go.Figure(
        go.Histogram(
            x=arr,
            histnorm="probability density",
            name="data",
            marker={"color": tokens.series[0], "line": {"color": tokens.surface, "width": 2}},
            opacity=0.85,
            hovertemplate="%{x}<br>density=%{y:.4g}<extra></extra>",
        )
    )
    for i, fit in enumerate(fits[:max_curves]):
        xs, ys = pdf_curve(fit, float(arr.min()), float(arr.max()))
        fig.add_trace(
            go.Scatter(
                x=xs,
                y=ys,
                mode="lines",
                name=f"{fit.name} (AIC {fit.aic:,.0f})",
                line={"color": tokens.series[(i + 1) % len(tokens.series)], "width": 2},
                hovertemplate=f"{fit.name}<br>pdf=%{{y:.4g}}<extra></extra>",
            )
        )
    return _base_layout(
        fig,
        title=f"Distribution of {column} with fitted candidates",
        x_title=column,
        y_title="density",
    )


def conversion_fig(variants: list[tuple[str, int, int]]) -> go.Figure:
    """Conversion rate per variant with 95% Wilson CIs.

    ``variants``: (name, conversions, n) per variant. Direct % labels satisfy the
    palette's relief rule for the sub-3:1 aqua slot.
    """
    tokens = _tokens()
    names = [v[0] for v in variants]
    rates: list[float] = []
    err_low: list[float] = []
    err_high: list[float] = []
    for _, conversions, n in variants:
        rate = conversions / n if n else float("nan")
        low, high = _wilson_ci(conversions, n)
        rates.append(rate)
        err_low.append(rate - low)
        err_high.append(high - rate)
    fig = go.Figure(
        go.Bar(
            x=names,
            y=rates,
            width=0.5,  # thin marks
            marker={
                "color": [color_for(names, name) for name in names],
                "line": {"color": tokens.surface, "width": 2},
            },
            text=[f"{r:.1%}" for r in rates],
            textposition="outside",
            textfont={"color": tokens.ink},
            error_y={
                "type": "data",
                "array": err_high,
                "arrayminus": err_low,
                "color": tokens.ink_muted,
                "thickness": 2,
            },
            hovertemplate="%{x}<br>rate=%{y:.2%}<extra></extra>",
        )
    )
    fig.update_yaxes(tickformat=".0%", rangemode="tozero")
    return _base_layout(
        fig,
        title="Conversion rate by variant (95% CI)",
        x_title="variant",
        y_title="conversion rate",
    )


def posterior_fig(
    a_alpha: float, a_beta: float, b_alpha: float, b_beta: float, name_a: str, name_b: str
) -> go.Figure:
    """Beta posterior densities of both variants' conversion rates (Bayesian view)."""
    means = [a_alpha / (a_alpha + a_beta), b_alpha / (b_alpha + b_beta)]
    spread = 4 * max(
        np.sqrt(means[0] * (1 - means[0]) / (a_alpha + a_beta)),
        np.sqrt(means[1] * (1 - means[1]) / (b_alpha + b_beta)),
    )
    xs = np.linspace(max(0.0, min(means) - spread), min(1.0, max(means) + spread), 400)
    names = sorted([name_a, name_b])
    fig = go.Figure()
    for name, (alpha_p, beta_p) in ((name_a, (a_alpha, a_beta)), (name_b, (b_alpha, b_beta))):
        fig.add_trace(
            go.Scatter(
                x=xs,
                y=scipy_stats.beta.pdf(xs, alpha_p, beta_p),
                mode="lines",
                name=name,
                line={"color": color_for(names, name), "width": 2},
                hovertemplate=f"{name}<br>rate=%{{x:.2%}}<extra></extra>",
            )
        )
    fig.update_xaxes(tickformat=".1%")
    return _base_layout(
        fig,
        title="Posterior conversion-rate densities (Beta-Binomial, uniform prior)",
        x_title="conversion rate",
        y_title="density",
    )


def missingness_fig(df: pd.DataFrame) -> go.Figure:
    """Share of missing values per column (single sequential hue — magnitude, not identity)."""
    tokens = _tokens()
    shares = df.isna().mean().sort_values(ascending=False)
    fig = go.Figure(
        go.Bar(
            x=list(shares.index.astype(str)),
            y=shares.to_numpy(),
            marker={"color": tokens.series[0], "line": {"color": tokens.surface, "width": 2}},
            text=[f"{v:.1%}" for v in shares],
            textposition="outside",
            textfont={"color": tokens.ink},
            hovertemplate="%{x}<br>missing=%{y:.2%}<extra></extra>",
        )
    )
    fig.update_yaxes(tickformat=".0%", rangemode="tozero")
    return _base_layout(fig, title="Missing values by column", x_title="column", y_title="missing")


def qq_fig(theoretical: np.ndarray, sample: np.ndarray, *, title: str) -> go.Figure:
    """Q-Q plot: ordered sample vs theoretical quantiles + y=x reference line."""
    tokens = _tokens()
    n = len(theoretical)
    scatter_cls = go.Scattergl if n > _WEBGL_THRESHOLD else go.Scatter
    lo = float(min(theoretical.min(), sample.min()))
    hi = float(max(theoretical.max(), sample.max()))
    fig = go.Figure()
    fig.add_trace(
        go.Scatter(
            x=[lo, hi],
            y=[lo, hi],
            mode="lines",
            name="y = x",
            line={"color": tokens.baseline, "width": 2, "dash": "dot"},
            hoverinfo="skip",
        )
    )
    fig.add_trace(
        scatter_cls(
            x=theoretical,
            y=sample,
            mode="markers",
            name="quantiles",
            marker={"color": tokens.series[0], "size": 8, "opacity": 0.6},
            hovertemplate="theoretical=%{x:.3g}<br>observed=%{y:.3g}<extra></extra>",
        )
    )
    return _base_layout(
        fig, title=title, x_title="theoretical quantiles", y_title="observed quantiles"
    )


def residuals_vs_fitted_fig(fitted: np.ndarray, residuals: np.ndarray) -> go.Figure:
    """Residuals vs fitted values with a zero reference line (heteroskedasticity screen)."""
    tokens = _tokens()
    scatter_cls = go.Scattergl if len(fitted) > _WEBGL_THRESHOLD else go.Scatter
    fig = go.Figure(
        scatter_cls(
            x=fitted,
            y=residuals,
            mode="markers",
            name="residuals",
            marker={"color": tokens.series[0], "size": 8, "opacity": 0.55},
            hovertemplate="fitted=%{x:.4g}<br>residual=%{y:.4g}<extra></extra>",
        )
    )
    fig.add_hline(y=0.0, line={"color": tokens.baseline, "width": 2, "dash": "dot"})
    return _base_layout(
        fig, title="Residuals vs fitted values", x_title="fitted", y_title="residual"
    )


def correlation_heatmap_fig(corr: pd.DataFrame) -> go.Figure:
    """Correlation matrix — diverging blue↔neutral↔red (polarity around 0)."""
    tokens = _tokens()
    diverging = [(0.0, tokens.series[0]), (0.5, tokens.diverging_mid), (1.0, tokens.series[5])]
    fig = go.Figure(
        go.Heatmap(
            z=corr.to_numpy(),
            x=list(corr.columns),
            y=list(corr.index),
            colorscale=diverging,
            zmin=-1,
            zmax=1,
            zmid=0,
            colorbar={"title": "r", "outlinewidth": 0},
            hovertemplate="%{y} × %{x}<br>r=%{z:.2f}<extra></extra>",
        )
    )
    return _base_layout(fig, title="Correlation matrix (Pearson)")


def _wilson_ci(successes: int, n: int, z: float = 1.96) -> tuple[float, float]:
    """Wilson score interval — behaves at the 0%/100% edges where Wald collapses."""
    if n == 0:
        return (float("nan"), float("nan"))
    phat = successes / n
    denom = 1.0 + z**2 / n
    center = (phat + z**2 / (2 * n)) / denom
    half = z * ((phat * (1 - phat) / n + z**2 / (4 * n**2)) ** 0.5) / denom
    return (max(0.0, center - half), min(1.0, center + half))
