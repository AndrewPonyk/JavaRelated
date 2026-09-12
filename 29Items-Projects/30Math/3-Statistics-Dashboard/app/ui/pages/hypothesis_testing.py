"""Hypothesis Testing — the flagship flow: profile → recommend → run → explain."""

from __future__ import annotations

import pandas as pd
import streamlit as st

from app.core.config import get_settings
from app.core.errors import guard_page
from app.services import analysis_service, report_service
from app.stats.profiler import VarKind, classify_column
from app.ui import state
from app.ui.components import charts, data_source_picker, result_card

_ALPHAS = (0.10, 0.05, 0.01)
_RESULT_KEY = "hypothesis_last_result"


@guard_page
def render() -> None:
    st.title("🧪 Hypothesis Testing")
    st.caption(
        "Pick an outcome and a grouping column — the right statistical test is selected "
        "automatically from the data's characteristics, and the rationale is shown."
    )

    df = data_source_picker.render(key_prefix="hypothesis")
    if df is None:
        st.info("Pick a demo dataset or upload a CSV to get started.")
        return

    overrides = state.get_kind_overrides()
    kinds = {col: overrides.get(col, classify_column(df[col])) for col in df.columns}
    outcome_options = [
        c
        for c, k in kinds.items()
        if k in (VarKind.CONTINUOUS, VarKind.BINARY, VarKind.CATEGORICAL, VarKind.ORDINAL)
    ]
    group_options = [
        c
        for c, k in kinds.items()
        if k in (VarKind.BINARY, VarKind.CATEGORICAL) and 2 <= df[c].nunique(dropna=True) <= 10
    ]
    if not outcome_options or not group_options:
        st.info(
            "This dataset needs at least one outcome column and one grouping column (2–10 levels)."
        )
        return

    default_alpha = get_settings().default_alpha
    with st.form("hypothesis_form"):
        col_outcome, col_group, col_alpha = st.columns((2, 2, 1))
        outcome = col_outcome.selectbox("Outcome (what you measure)", outcome_options)
        group = col_group.selectbox(
            "Group (what you compare)",
            [c for c in group_options if c != outcome] or group_options,
        )
        alpha = col_alpha.selectbox(
            "α",
            _ALPHAS,
            index=_ALPHAS.index(default_alpha) if default_alpha in _ALPHAS else 1,
        )
        paired = st.checkbox("Samples are paired (matched by row order)", value=False)
        submitted = st.form_submit_button("Run analysis", type="primary")

    if submitted:
        dataset_id = state.get_active_dataset_id()
        bundle = analysis_service.run_group_comparison(
            df,
            outcome,
            group,
            paired=paired,
            alpha=float(alpha),
            dataset_id=dataset_id,
            outcome_kind_override=overrides.get(outcome),
        )
        # Capture persistence at submit time — session state may change afterwards.
        persisted = dataset_id is not None and not get_settings().demo_mode
        state.remember(_RESULT_KEY, (bundle, state.get_active_df_name(), df, persisted))

    stored = state.recall(_RESULT_KEY)
    if stored is None:
        return
    bundle, dataset_name, analyzed_df, persisted = stored

    if bundle.profile.outcome in overrides:
        st.caption(
            f"Column-kind override applied: '{bundle.profile.outcome}' analyzed as "
            f"**{bundle.profile.outcome_kind.value}**."
        )
    result_card.render(bundle)
    _render_posthoc(bundle)
    _render_chart(bundle, analyzed_df)

    st.download_button(
        "⬇️ Download HTML report",
        data=report_service.render_html_report(bundle, dataset_name),
        file_name=report_service.report_filename(bundle),
        mime="text/html",
    )
    if persisted:
        st.caption("💾 Recorded in the analysis history (see Home → Recent analyses).")
    st.caption(f"Result computed on: {dataset_name}")


def _render_posthoc(bundle: analysis_service.AnalysisBundle) -> None:
    if not bundle.posthoc:
        return
    st.subheader("Pairwise follow-ups (Holm-corrected)")
    table = pd.DataFrame([p.to_json_dict() for p in bundle.posthoc]).rename(
        columns={
            "group_a": "group A",
            "group_b": "group B",
            "p_raw": "p (raw)",
            "p_adjusted": "p (adjusted)",
        }
    )
    st.dataframe(table, use_container_width=True, hide_index=True)
    st.caption("Holm step-down correction keeps the family-wise error rate at α.")


def _render_chart(bundle: analysis_service.AnalysisBundle, df: pd.DataFrame) -> None:
    profile = bundle.profile
    if profile.outcome not in df.columns or profile.group not in df.columns:
        return
    if profile.outcome_kind in (VarKind.CONTINUOUS, VarKind.ORDINAL):
        st.plotly_chart(
            charts.group_comparison_fig(df, profile.outcome, profile.group),
            use_container_width=True,
        )
    else:
        st.plotly_chart(
            charts.proportion_by_group_fig(df, profile.outcome, profile.group),
            use_container_width=True,
        )
