"""Model insights page: global SHAP importance + holdout metrics + recent activity.

Demonstrates a data-fetch (from DB) → display pattern with caching and graceful
empty/error states.
"""

from __future__ import annotations

import pandas as pd
import streamlit as st

from auth import require_auth
from churn_predictor import ChurnError
from churn_predictor.data.loader import load_customers
from churn_predictor.models.explain import global_importance
from churn_predictor.models.predict import load_artifact

st.set_page_config(page_title="Model Insights", page_icon="📊", layout="wide")
require_auth()
st.title("📊 Model Insights")


@st.cache_data(ttl=600, show_spinner="Loading reference data…")
def load_sample(limit: int = 2000) -> pd.DataFrame:
    """Fetch a sample of customers for global explanation (cached 10 min)."""
    return load_customers(source="postgres", require_target=False, limit=limit)


@st.cache_data(ttl=120, show_spinner=False)
def load_recent() -> list[dict]:
    from churn_predictor.db.repository import recent_predictions

    return recent_predictions(limit=25)


# --- Holdout metrics from artifact metadata --------------------------------
try:
    meta = load_artifact()["metadata"]
except ChurnError as exc:
    st.error(f"Model not available: {exc}")
    st.stop()

st.subheader("Holdout performance")
c1, c2, c3, c4 = st.columns(4)
c1.metric("Algorithm", meta.get("algorithm", "—"))
c2.metric("ROC-AUC", f"{meta['metrics'].get('holdout_roc_auc', 0):.3f}")
c3.metric("PR-AUC", f"{meta['metrics'].get('holdout_pr_auc', 0):.3f}")
c4.metric("Accuracy", f"{meta['metrics'].get('holdout_accuracy', 0):.3f}")
st.caption(
    f"Trained on {meta.get('n_train', '?')} rows · "
    f"positive (churn) rate {meta.get('positive_rate', 0):.1%}"
)

# --- Global SHAP importance -------------------------------------------------
st.subheader("Global feature importance (mean |SHAP|)")
try:
    raw = load_sample()
    importance = global_importance(raw)
    st.bar_chart(importance.set_index("feature")["mean_abs_shap"])
    st.dataframe(importance, use_container_width=True, hide_index=True)
except ChurnError as exc:
    st.warning(f"Could not compute SHAP importance: {exc}")
except Exception as exc:  # noqa: BLE001 - last-resort UI guard
    st.warning(f"No data available for explanations yet: {exc}")

# --- Recent prediction activity --------------------------------------------
st.subheader("Recent predictions")
try:
    recent = load_recent()
    if recent:
        st.dataframe(pd.DataFrame(recent), use_container_width=True, hide_index=True)
    else:
        st.info("No predictions have been saved yet.")
except Exception as exc:  # noqa: BLE001
    st.info(f"Prediction history unavailable: {exc}")
