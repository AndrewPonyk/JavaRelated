"""Data Explorer: summaries, missingness, group-by, correlations, and kind overrides."""

from __future__ import annotations

import pandas as pd
import streamlit as st
from pandas.api import types as ptypes

from app.core.errors import guard_page
from app.stats.profiler import VarKind, classify_column
from app.ui import state
from app.ui.components import charts, data_source_picker

_OVERRIDE_CHOICES = ("auto", "continuous", "binary", "categorical", "ordinal")


@guard_page
def render() -> None:
    st.title("🔍 Data Explorer")

    df = data_source_picker.render(key_prefix="explorer")
    if df is None:
        st.info("Pick a demo dataset or upload a CSV to explore.")
        return

    tab_summary, tab_missing, tab_groupby, tab_corr, tab_kinds = st.tabs(
        ["Summary", "Missingness", "Group-by", "Correlations", "Column kinds"]
    )

    with tab_summary:
        st.dataframe(df.describe(include="all").T, use_container_width=True)

    with tab_missing:
        if int(df.isna().sum().sum()) == 0:
            st.success("No missing values in this dataset.", icon="✅")
        else:
            st.plotly_chart(charts.missingness_fig(df), use_container_width=True)

    with tab_groupby:
        _render_groupby(df)

    with tab_corr:
        numeric = df.select_dtypes("number")
        if numeric.shape[1] < 2:
            st.info("Need at least two numeric columns for a correlation matrix.")
        else:
            st.plotly_chart(
                charts.correlation_heatmap_fig(numeric.corr()), use_container_width=True
            )

    with tab_kinds:
        _render_kind_overrides(df)


def _render_groupby(df: pd.DataFrame) -> None:
    group_candidates = [c for c in df.columns if 2 <= df[c].nunique(dropna=True) <= 30]
    numeric_cols = [c for c in df.columns if ptypes.is_numeric_dtype(df[c])]
    if not group_candidates or not numeric_cols:
        st.info("Group-by summaries need a low-cardinality column and at least one numeric column.")
        return
    group = st.selectbox("Group by", group_candidates, key="explorer_groupby")
    metrics = [c for c in numeric_cols if c != group]
    if not metrics:
        st.info("No numeric columns left to summarize for this grouping.")
        return
    summary = df.groupby(group)[metrics].agg(["count", "mean", "median", "std"])
    summary.columns = [f"{col} · {stat}" for col, stat in summary.columns]
    st.dataframe(summary, use_container_width=True)


def _render_kind_overrides(df: pd.DataFrame) -> None:
    st.markdown(
        "The heuristic classification drives automated test selection. Correct it here — "
        "e.g. mark an int-coded Likert column **ordinal** (numeric columns only) so "
        "rank-based tests are used."
    )
    overrides = state.get_kind_overrides()
    for column in df.columns:
        inferred = classify_column(df[column])
        numeric = ptypes.is_numeric_dtype(df[column])
        choices = [c for c in _OVERRIDE_CHOICES if c != "ordinal" or numeric]
        current = overrides.get(column)
        index = choices.index(current.value) if current and current.value in choices else 0
        left, right = st.columns((2, 3))
        left.markdown(f"`{column}`  \n*inferred: {inferred.value}*")
        choice = right.selectbox(
            f"Kind for {column}",
            choices,
            index=index,
            key=f"kind_override_{column}",
            label_visibility="collapsed",
        )
        state.set_kind_override(column, None if choice == "auto" else VarKind(choice))
    if state.get_kind_overrides():
        st.caption(
            "Overrides apply to analyses in this browser session: "
            + ", ".join(f"{c} → {k.value}" for c, k in state.get_kind_overrides().items())
        )
