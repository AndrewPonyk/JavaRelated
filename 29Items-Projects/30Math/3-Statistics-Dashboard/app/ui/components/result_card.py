"""Uniform presentation of a test result: verdict, p, statistic, effect size, rationale.

The card always shows the effect size next to the p-value (TECH-NOTES §3.6.13)
and the automated-selection rationale verbatim (trust is a feature).
"""

from __future__ import annotations

import streamlit as st

from app.services.analysis_service import AnalysisBundle


def _format_p(p: float) -> str:
    return "< 0.0001" if p < 1e-4 else f"{p:.4f}"


def render(bundle: AnalysisBundle) -> None:
    result, recommendation = bundle.result, bundle.recommendation

    with st.container(border=True):
        st.subheader(result.label)

        verdict = (
            "Statistically significant" if result.significant else "Not statistically significant"
        )
        icon = "✅" if result.significant else "⬜"
        st.markdown(f"{icon} **{verdict}** at α = {result.alpha:g}")

        col_p, col_stat, col_effect = st.columns(3)
        col_p.metric("p-value", _format_p(result.p_value))
        col_stat.metric("Test statistic", f"{result.statistic:.4g}")
        if result.effect_size is not None:
            col_effect.metric(
                result.effect_size.name,
                f"{result.effect_size.value:.3f}",
                help=f"Magnitude: {result.effect_size.magnitude}",
            )

        st.caption(result.interpretation)

        with st.expander("Why this test? (automated selection rationale)"):
            for reason in recommendation.reasons:
                st.markdown(f"- {reason}")
            if recommendation.fallbacks:
                alternatives = ", ".join(f.value for f in recommendation.fallbacks)
                st.markdown(f"*Alternatives considered:* {alternatives}")

        for warning in (*recommendation.warnings, *result.warnings):
            st.warning(warning, icon="⚠️")

        st.caption(f"Computed in {bundle.duration_ms} ms")
