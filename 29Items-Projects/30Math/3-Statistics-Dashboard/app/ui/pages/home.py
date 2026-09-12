"""Landing page: pick data, see its profile, review recent analyses."""

from __future__ import annotations

from dataclasses import asdict

import pandas as pd
import streamlit as st

from app.core.config import get_settings
from app.core.errors import guard_page
from app.services import analysis_service
from app.stats.profiler import profile_dataframe
from app.ui.components import data_source_picker


@guard_page
def render() -> None:
    st.title("📊 Statistics Dashboard")
    st.caption(
        "Self-service statistical analysis — hypothesis testing, regression, "
        "distribution fitting, and A/B tests with automated test selection."
    )

    if get_settings().demo_mode:
        st.info(
            "Running in **demo mode** (no database configured) — analyses run fully "
            "in-memory and are not persisted.",
            icon="ℹ️",
        )

    df = data_source_picker.render(key_prefix="home")
    if df is None:
        return

    left, right = st.columns((3, 2), gap="large")
    with left:
        st.subheader("Preview")
        st.dataframe(df.head(20), use_container_width=True)
    with right:
        st.subheader("Column profile")
        profiles = pd.DataFrame([asdict(p) for p in profile_dataframe(df)])
        st.dataframe(profiles, use_container_width=True, hide_index=True)
        st.caption(
            "Column kinds drive automated test selection — override them in the "
            "*Data Explorer* if the heuristic guessed wrong."
        )

    _render_recent_runs()

    st.divider()
    st.markdown(
        "**Where next:** 🔍 *Data Explorer* for summaries · 🧪 *Hypothesis Testing* for "
        "automated group comparisons · 📈 *Regression* · 📉 *Distribution Fitting* · "
        "⚖️ *A/B Testing* for experiment analysis and planning · ⚗️ *Experiments* for the registry."
    )


def _render_recent_runs() -> None:
    if get_settings().demo_mode:
        return
    runs = analysis_service.list_recent_runs(limit=10)
    if not runs:
        return
    st.subheader("Recent analyses")
    table = pd.DataFrame([asdict(r) for r in runs]).rename(
        columns={
            "kind": "kind",
            "dataset_name": "dataset",
            "headline": "result",
            "created_at": "when",
            "duration_ms": "ms",
        }
    )
    st.dataframe(table, use_container_width=True, hide_index=True)
