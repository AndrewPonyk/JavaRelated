"""Customer Churn Predictor — Streamlit entrypoint (Home).

Thin presentation layer over the ``churn_predictor`` package. Demonstrates the
data-fetch → display → error/loading pattern and resource caching.
"""

from __future__ import annotations

import streamlit as st

from auth import require_auth
from churn_predictor import ChurnError, ModelArtifactError
from churn_predictor.config import configure_logging
from churn_predictor.models.predict import load_artifact

configure_logging()

st.set_page_config(
    page_title="Customer Churn Predictor",
    page_icon="📉",
    layout="wide",
)


@st.cache_resource(show_spinner="Loading model…")
def cached_artifact() -> dict:
    """Load the model once per process (cached across reruns)."""
    return load_artifact()


def main() -> None:
    require_auth()

    st.title("📉 Customer Churn Predictor")
    st.caption(
        "Self-service churn scoring with SHAP explanations and actionable "
        "retention recommendations."
    )

    # --- Model status panel (loading / error / success states) -------------
    with st.status("Checking model availability…", expanded=False) as status:
        try:
            bundle = cached_artifact()
            meta = bundle["metadata"]
            status.update(label="Model loaded", state="complete")
        except ModelArtifactError:
            status.update(label="No trained model found", state="error")
            st.warning(
                "No model artifact is available yet. Train a model "
                "(`python -m churn_predictor.bootstrap` or `make train`) or configure "
                "`MODEL_REMOTE_URL`."
            )
            return
        except ChurnError as exc:  # domain errors -> friendly message
            status.update(label="Model load failed", state="error")
            st.error(f"Could not load the model: {exc}")
            return

    col1, col2, col3, col4 = st.columns(4)
    col1.metric("Algorithm", meta.get("algorithm", "—"))
    col2.metric("Holdout AUC", f"{meta['metrics'].get('holdout_roc_auc', 0):.3f}")
    col3.metric("Holdout PR-AUC", f"{meta['metrics'].get('holdout_pr_auc', 0):.3f}")
    col4.metric("Trained (UTC)", str(meta.get("trained_at", "—"))[:19])

    st.divider()
    st.subheader("Get started")
    st.markdown("""
        - **Predict** — score a single customer and see retention actions.
        - **Model Insights** — global SHAP importance and holdout metrics.
        - **Batch Scoring** — upload a CSV and score many customers at once.

        Use the sidebar to navigate. ←
        """)


if __name__ == "__main__":
    main()
