"""Regression Analysis: OLS and logistic models with residual diagnostics and VIF."""

from __future__ import annotations

import streamlit as st

from app.core.errors import guard_page
from app.services import analysis_service
from app.stats import regression as reg
from app.stats.profiler import VarKind, classify_column
from app.ui import state
from app.ui.components import charts, data_source_picker

_RESULT_KEY = "regression_last_result"


@guard_page
def render() -> None:
    st.title("📈 Regression Analysis")

    df = data_source_picker.render(key_prefix="regression")
    if df is None:
        st.info("Pick a demo dataset or upload a CSV to get started.")
        return

    kinds = {col: classify_column(df[col]) for col in df.columns}
    continuous = [c for c, k in kinds.items() if k == VarKind.CONTINUOUS]
    binary = [c for c, k in kinds.items() if k == VarKind.BINARY]

    with st.form("regression_form"):
        model_choice = st.radio(
            "Model",
            ("OLS (continuous outcome)", "Logistic (binary outcome)"),
            horizontal=True,
        )
        is_logistic = model_choice.startswith("Logistic")
        outcome_options = binary if is_logistic else continuous
        if not outcome_options:
            st.info("No suitable outcome column for this model type in the active dataset.")
            st.form_submit_button("Fit model", disabled=True)
            return
        outcome = st.selectbox("Outcome", outcome_options)
        features = st.multiselect(
            "Features",
            # IDs carry no signal; raw datetimes need feature engineering first.
            [
                c
                for c in df.columns
                if c != outcome and kinds[c] not in (VarKind.ID, VarKind.DATETIME)
            ],
        )
        submitted = st.form_submit_button("Fit model", type="primary")

    if submitted:
        result = analysis_service.run_regression(
            df,
            outcome,
            features,
            model_kind="logistic" if is_logistic else "ols",
            dataset_id=state.get_active_dataset_id(),
        )
        state.remember(_RESULT_KEY, (result, state.get_active_df_name()))

    stored = state.recall(_RESULT_KEY)
    if stored is None:
        return
    result, dataset_name = stored

    st.code(result.formula, language="text")
    col_n, col_fit = st.columns(2)
    col_n.metric("Observations", f"{result.n_obs:,}")
    if result.r_squared is not None:
        col_fit.metric("R² (adj.)", f"{result.r_squared:.3f} ({result.adj_r_squared:.3f})")
    if result.pseudo_r_squared is not None:
        col_fit.metric("Pseudo R²", f"{result.pseudo_r_squared:.3f}")

    st.dataframe(
        result.coefficients.style.format(
            {c: "{:.4g}" for c in result.coefficients.columns if c != "term"}
        ),
        use_container_width=True,
        hide_index=True,
    )

    _render_diagnostics(result)
    st.caption(f"Model fitted on: {dataset_name}")


def _render_diagnostics(result: reg.RegressionResult) -> None:
    if result.diagnostics:
        st.subheader("Diagnostics")
        cols = st.columns(max(len(result.diagnostics), 1))
        labels = {
            "durbin_watson": ("Durbin-Watson", "≈2 means no autocorrelation"),
            "breusch_pagan_p": ("Breusch-Pagan p", "small p ⇒ heteroskedastic residuals"),
            "jarque_bera_p": ("Jarque-Bera p", "small p ⇒ non-normal residuals"),
            "log_likelihood": ("Log-likelihood", "model fit (higher is better)"),
        }
        for col, (key, value) in zip(cols, result.diagnostics.items(), strict=False):
            label, help_text = labels.get(key, (key, ""))
            col.metric(label, f"{value:.4g}", help=help_text)

    if result.vif is not None:
        st.subheader("Multicollinearity (VIF)")
        st.dataframe(
            result.vif.style.format({"vif": "{:.2f}"}),
            use_container_width=True,
            hide_index=True,
        )
        if float(result.vif["vif"].max()) > 5.0:
            st.warning(
                "A VIF above 5 signals collinear features — coefficient estimates are "
                "unstable; consider dropping or combining features.",
                icon="⚠️",
            )

    if result.model_kind == "ols" and result.residuals.size >= 3:
        left, right = st.columns(2)
        with left:
            st.plotly_chart(
                charts.residuals_vs_fitted_fig(result.fitted, result.residuals),
                use_container_width=True,
            )
        with right:
            theoretical, ordered = reg.qq_points(result.residuals)
            st.plotly_chart(
                charts.qq_fig(theoretical, ordered, title="Residual Q-Q plot (normal)"),
                use_container_width=True,
            )
