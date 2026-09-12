"""Batch scoring page: upload a CSV, score all rows, optionally write to DB.

Demonstrates file upload → validation → vectorized scoring → download/persist,
with explicit operator confirmation before any DB write.
"""

from __future__ import annotations

import pandas as pd
import streamlit as st

from auth import require_auth
from churn_predictor import ChurnError
from churn_predictor.data.loader import EXPECTED_COLUMNS
from churn_predictor.db.repository import PredictionRecord, save_predictions_bulk
from churn_predictor.models.predict import load_artifact, predict_frame

st.set_page_config(page_title="Batch Scoring", page_icon="📦", layout="wide")
require_auth()
st.title("📦 Batch Scoring")

REQUIRED = [c for c in EXPECTED_COLUMNS if c != "is_churned"]
st.markdown(
    "Upload a CSV of customers to score in bulk. Required columns: " f"`{', '.join(REQUIRED)}`."
)

# Offer a template so users know the exact schema.
template = pd.DataFrame(
    [
        {
            "customer_id": "CUST-0001",
            "tenure_months": 5,
            "monthly_charges": 89.5,
            "total_charges": 447.5,
            "contract_type": "Month-to-month",
            "payment_method": "Electronic check",
            "num_support_tickets": 4,
        }
    ]
)
st.download_button(
    "⬇️ Download CSV template",
    template.to_csv(index=False).encode("utf-8"),
    file_name="customers_template.csv",
    mime="text/csv",
)

uploaded = st.file_uploader("Customer CSV", type=["csv"])

if uploaded is not None:
    try:
        df = pd.read_csv(uploaded)
    except Exception as exc:  # noqa: BLE001
        st.error(f"Could not parse CSV: {exc}")
        st.stop()

    missing = set(REQUIRED) - set(df.columns)
    if missing:
        st.error(f"Missing required columns: {sorted(missing)}")
        st.stop()

    st.write(f"Loaded **{len(df)}** rows.")
    st.dataframe(df.head(), use_container_width=True)

    if st.button("Score all rows", type="primary"):
        try:
            with st.spinner("Scoring…"):
                scored = predict_frame(df)
                algo = load_artifact()["metadata"]["algorithm"]
        except ChurnError as exc:
            st.error(f"Scoring failed: {exc}")
            st.stop()

        st.success("Scoring complete.")
        st.dataframe(
            scored.sort_values("churn_probability", ascending=False),
            use_container_width=True,
            hide_index=True,
        )
        st.download_button(
            "Download scored CSV",
            scored.to_csv(index=False).encode("utf-8"),
            file_name="scored_customers.csv",
            mime="text/csv",
        )
        st.session_state["scored"] = scored
        st.session_state["algo"] = algo

# --- Optional persistence (explicit, separate confirmation) ----------------
if "scored" in st.session_state:
    st.divider()
    confirm = st.checkbox("I want to persist these predictions to the database")
    if confirm and st.button("Write to database"):
        scored = st.session_state["scored"]
        # Batch scoring persists scores without per-row SHAP drivers by design:
        # computing SHAP for every row is expensive. Use the Predict page for a
        # single explained + saved prediction.
        records = [
            PredictionRecord(
                customer_id=str(row.get("customer_id", "")),
                churn_probability=float(row["churn_probability"]),
                risk_band=str(row["risk_band"]),
                model_algorithm=st.session_state["algo"],
                top_drivers=[],
                actor="batch-ui",
            )
            for _, row in scored.iterrows()
        ]
        try:
            n = save_predictions_bulk(records)
            st.success(f"Wrote {n} predictions to the database.")
        except Exception as exc:  # noqa: BLE001 - surface DB errors gracefully
            st.error(f"Database write failed (is the database reachable?): {exc}")
