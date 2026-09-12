"""Distribution Fitting: rank candidate distributions for a numeric column."""

from __future__ import annotations

import pandas as pd
import streamlit as st

from app.core.errors import guard_page
from app.services import analysis_service
from app.stats.distributions import CANDIDATES, qq_points
from app.stats.profiler import VarKind, classify_column
from app.ui import state
from app.ui.components import charts, data_source_picker

_RESULT_KEY = "distribution_last_result"


@guard_page
def render() -> None:
    st.title("📉 Distribution Fitting")

    df = data_source_picker.render(key_prefix="distfit")
    if df is None:
        st.info("Pick a demo dataset or upload a CSV to get started.")
        return

    numeric_cols = [c for c in df.columns if classify_column(df[c]) == VarKind.CONTINUOUS]
    if not numeric_cols:
        st.info("This dataset has no continuous numeric column to fit.")
        return

    with st.form("distfit_form"):
        column = st.selectbox("Column to fit", numeric_cols)
        candidate_names = st.multiselect(
            "Candidate distributions",
            list(CANDIDATES.keys()),
            default=list(CANDIDATES.keys()),
            help="Candidates whose support doesn't match the data (e.g. beta outside (0,1)) "
            "are skipped automatically.",
        )
        submitted = st.form_submit_button("Fit distributions", type="primary")

    if submitted:
        fits = analysis_service.run_distribution_fit(
            df,
            column,
            candidate_names=candidate_names,
            dataset_id=state.get_active_dataset_id(),
        )
        values = df[column].dropna().to_numpy(dtype=float)
        state.remember(_RESULT_KEY, (fits, column, values, state.get_active_df_name()))

    stored = state.recall(_RESULT_KEY)
    if stored is None:
        return
    fits, column, values, dataset_name = stored

    if not fits:
        st.warning(
            "No candidate distribution could be fitted — the selected candidates' support "
            "may not match this column's values."
        )
        return

    table = pd.DataFrame(
        {
            "distribution": [f.name for f in fits],
            "AIC": [f.aic for f in fits],
            "KS statistic": [f.ks_statistic for f in fits],
            "KS p-value": [f.ks_p_value for f in fits],
            "parameters": [", ".join(f"{p:.4g}" for p in f.params) for f in fits],
        }
    )
    st.dataframe(table, use_container_width=True, hide_index=True)

    left, right = st.columns(2)
    with left:
        st.plotly_chart(
            charts.histogram_with_fits_fig(values, fits, column=column),
            use_container_width=True,
        )
    with right:
        best = fits[0]
        theoretical, ordered = qq_points(best, values)
        st.plotly_chart(
            charts.qq_fig(theoretical, ordered, title=f"Q-Q plot vs best fit ({best.name})"),
            use_container_width=True,
        )
    st.caption(
        "Ranked by AIC (lower is better). KS values are diagnostic only — parameters "
        f"were estimated from this same sample, which biases the KS p-value. Data: {dataset_name}"
    )
