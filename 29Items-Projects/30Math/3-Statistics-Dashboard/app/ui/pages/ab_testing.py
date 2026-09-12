"""A/B Testing: analyze a finished experiment (frequentist + Bayesian) and plan a new one."""

from __future__ import annotations

import streamlit as st

from app.core.config import get_settings
from app.core.errors import AppError, guard_page
from app.services import ab_testing_service, experiment_service
from app.stats.profiler import VarKind, classify_column
from app.ui import state
from app.ui.components import charts, data_source_picker

_ALPHAS = (0.10, 0.05, 0.01)
_RESULT_KEY = "ab_last_result"
_NO_EXPERIMENT = "— none —"


@guard_page
def render() -> None:
    st.title("⚖️ A/B Testing")

    tab_analyze, tab_plan = st.tabs(["Analyze results", "Plan an experiment"])

    with tab_analyze:
        _render_analyze()

    with tab_plan:
        _render_plan()


def _render_analyze() -> None:
    df = data_source_picker.render(key_prefix="ab")
    if df is None:
        st.info("Pick a demo dataset or upload a CSV with a variant column and a 0/1 metric.")
        return

    kinds = {col: classify_column(df[col]) for col in df.columns}
    two_level_cols = [c for c in df.columns if df[c].nunique(dropna=True) == 2]
    binary_cols = [c for c, k in kinds.items() if k == VarKind.BINARY]
    if not two_level_cols or not binary_cols:
        st.info("Need a two-level variant column and a binary (0/1) outcome column.")
        return

    experiments = {
        e.name: e for e in experiment_service.list_experiments() if e.status != "completed"
    }
    with st.form("ab_analyze_form"):
        col_variant, col_metric, col_alpha = st.columns((2, 2, 1))
        variant_col = col_variant.selectbox("Variant column", two_level_cols)
        metric_col = col_metric.selectbox(
            "Conversion metric (0/1)", [c for c in binary_cols if c != variant_col] or binary_cols
        )
        alpha = col_alpha.selectbox("α", _ALPHAS, index=1)
        experiment_name = _NO_EXPERIMENT
        if experiments:
            experiment_name = st.selectbox(
                "Experiment (optional — brings its planned n and split, enables logging)",
                [_NO_EXPERIMENT, *experiments.keys()],
            )
        submitted = st.form_submit_button("Analyze", type="primary")

    if submitted:
        experiment = experiments.get(experiment_name)
        analysis = ab_testing_service.analyze_conversion(
            df,
            variant_col,
            metric_col,
            alpha=float(alpha),
            expected_ratio=experiment.expected_ratio if experiment else 0.5,
            planned_n_per_variant=experiment.planned_n_per_variant if experiment else None,
        )
        state.remember(
            _RESULT_KEY,
            {
                "analysis": analysis,
                "variant_col": variant_col,
                "metric_col": metric_col,
                "experiment_id": experiment.id if experiment else None,
                "dataset_name": state.get_active_df_name(),
                "df": df,  # the exact frame analyzed — what experiment logging snapshots
            },
        )

    stored = state.recall(_RESULT_KEY)
    if stored is None:
        return
    analysis = stored["analysis"]
    a, b = analysis.variant_a, analysis.variant_b

    if not analysis.srm.passed:
        st.error(
            f"Sample-ratio mismatch (p = {analysis.srm.p_value:.2g}) — the traffic split "
            "is broken; do not trust this result.",
            icon="🚨",
        )

    col_a, col_b, col_p = st.columns(3)
    col_a.metric(f"{a.name} rate", f"{a.rate:.2%}", help=f"{a.conversions:,} / {a.n:,}")
    col_b.metric(
        f"{b.name} rate",
        f"{b.rate:.2%}",
        delta=f"{analysis.absolute_lift * 100:+.2f} pp",
        help=f"{b.conversions:,} / {b.n:,}",
    )
    col_p.metric("p-value", "< 0.0001" if analysis.p_value < 1e-4 else f"{analysis.p_value:.4f}")

    verdict = "✅ Significant" if analysis.significant else "⬜ Not significant"
    relative = (
        f" · relative lift {analysis.relative_lift:+.1%}"
        if analysis.relative_lift == analysis.relative_lift
        else ""
    )
    st.markdown(
        f"**{verdict}** at α = {analysis.alpha:g}{relative} · 95% CI on lift: "
        f"[{analysis.lift_ci_low * 100:+.2f} pp, {analysis.lift_ci_high * 100:+.2f} pp]"
    )

    for warning in analysis.warnings:
        st.warning(warning, icon="⚠️")

    left, right = st.columns(2)
    with left:
        st.plotly_chart(
            charts.conversion_fig([(a.name, a.conversions, a.n), (b.name, b.conversions, b.n)]),
            use_container_width=True,
        )
    with right:
        _render_bayesian(analysis)

    _render_experiment_logging(stored)

    st.caption(
        "Decide the sample size before starting and analyze once at the planned n — "
        "peeking until p < 0.05 manufactures false positives (see docs/TECH-NOTES.md). "
        f"Data: {stored['dataset_name']}"
    )


def _render_bayesian(analysis: ab_testing_service.ConversionAnalysis) -> None:
    bayes = analysis.bayesian
    b_name = analysis.variant_b.name
    st.plotly_chart(
        charts.posterior_fig(
            bayes.a_alpha,
            bayes.a_beta,
            bayes.b_alpha,
            bayes.b_beta,
            analysis.variant_a.name,
            analysis.variant_b.name,
        ),
        use_container_width=True,
    )
    col_prob, col_lift = st.columns(2)
    col_prob.metric(f"P({b_name} beats {analysis.variant_a.name})", f"{bayes.prob_b_beats_a:.1%}")
    col_lift.metric(
        "Expected lift (posterior)",
        f"{bayes.expected_lift * 100:+.2f} pp",
        help=(
            f"95% credible interval: [{bayes.lift_ci_low * 100:+.2f}, "
            f"{bayes.lift_ci_high * 100:+.2f}] pp"
        ),
    )


def _render_experiment_logging(stored: dict) -> None:
    experiment_id = stored.get("experiment_id")
    if experiment_id is None or get_settings().demo_mode:
        return
    if st.button("📌 Log this result to the experiment"):
        try:
            run_id = ab_testing_service.log_experiment_run(
                stored["df"],
                stored["dataset_name"],
                stored["analysis"],
                experiment_id,
                variant_col=stored["variant_col"],
                outcome_col=stored["metric_col"],
            )
            st.toast(f"Logged run {run_id[:8]}… with its data snapshot.", icon="📌")
        except AppError as exc:
            st.error(exc.user_message, icon="⚠️")


def _render_plan() -> None:
    st.markdown("How many users per variant do you need before starting the experiment?")

    col1, col2, col3, col4 = st.columns(4)
    baseline_pct = col1.number_input("Baseline rate (%)", 0.1, 99.0, 10.0, step=0.5)
    mde_pct = col2.number_input("MDE, relative (%)", 1.0, 100.0, 10.0, step=1.0)
    alpha = col3.selectbox("α", _ALPHAS, index=1, key="plan_alpha")
    power = col4.selectbox("Power", (0.80, 0.90), index=0, format_func=lambda v: f"{v:.0%}")

    try:
        n = ab_testing_service.required_sample_size(
            baseline_pct / 100.0, mde_pct / 100.0, alpha=float(alpha), power=float(power)
        )
    except AppError as exc:
        st.warning(exc.user_message, icon="⚠️")
        return

    col_n, col_total = st.columns(2)
    col_n.metric("Per variant", f"{n:,}")
    col_total.metric("Total (2 variants)", f"{2 * n:,}")
    st.caption(
        f"Detects a {mde_pct:g}% relative lift over a {baseline_pct:g}% baseline with "
        f"{power:.0%} power at α = {alpha:g} (two-sided). Register the experiment with this "
        "planned n on the ⚗️ Experiments page to get interim-look warnings."
    )
