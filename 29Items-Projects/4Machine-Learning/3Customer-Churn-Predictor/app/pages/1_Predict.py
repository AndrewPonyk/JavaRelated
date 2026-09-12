"""Single-customer prediction page.

Demonstrates: input form + validation → predict → SHAP explanation →
retention recommendations → optional persistence. Includes loading/error states.
"""

from __future__ import annotations

import pandas as pd
import streamlit as st

from auth import require_auth
from churn_predictor import ChurnError
from churn_predictor.db.repository import PredictionRecord, save_prediction
from churn_predictor.models.explain import explain_one
from churn_predictor.models.predict import load_artifact, predict_one
from churn_predictor.retention.recommendations import recommend

st.set_page_config(page_title="Predict — Churn", page_icon="🔮", layout="wide")
require_auth()
st.title("🔮 Single Customer Prediction")

with st.form("predict_form"):
    c1, c2 = st.columns(2)
    customer_id = c1.text_input("Customer ID", value="CUST-0001")
    tenure_months = c1.number_input("Tenure (months)", min_value=0, max_value=120, value=12)
    monthly_charges = c1.number_input("Monthly charges", min_value=0.0, value=70.0)
    total_charges = c2.number_input("Total charges", min_value=0.0, value=840.0)
    num_support_tickets = c2.number_input("Support tickets", min_value=0, value=2)
    contract_type = c2.selectbox("Contract type", ["Month-to-month", "One year", "Two year"])
    payment_method = c1.selectbox(
        "Payment method",
        ["Electronic check", "Mailed check", "Bank transfer", "Credit card"],
    )
    submitted = st.form_submit_button("Predict churn", type="primary")

if submitted:
    if not customer_id.strip():
        st.error("Customer ID is required.")
        st.stop()

    record = {
        "customer_id": customer_id,
        "tenure_months": tenure_months,
        "monthly_charges": monthly_charges,
        "total_charges": total_charges,
        "num_support_tickets": num_support_tickets,
        "contract_type": contract_type,
        "payment_method": payment_method,
    }

    try:
        with st.spinner("Scoring customer…"):
            prediction = predict_one(record)
            drivers = explain_one(pd.DataFrame([record]))
            actions = recommend(drivers)
            algorithm = load_artifact()["metadata"]["algorithm"]
    except ChurnError as exc:
        st.error(f"Prediction failed: {exc}")
        st.stop()

    # Stash results so the persistence button (a rerun) can access them.
    st.session_state["last_prediction"] = {
        "record": record,
        "prediction": prediction,
        "drivers": drivers,
        "actions": actions,
        "algorithm": algorithm,
    }

# Render the most recent prediction (survives reruns triggered by the save button).
state = st.session_state.get("last_prediction")
if state:
    prediction = state["prediction"]
    drivers = state["drivers"]
    actions = state["actions"]

    band_icon = {"high": "🔴", "medium": "🟠", "low": "🟢"}[prediction.risk_band]
    m1, m2 = st.columns(2)
    m1.metric("Churn probability", f"{prediction.probability:.1%}")
    m2.metric("Risk band", f"{band_icon} {prediction.risk_band.title()}")

    st.subheader("Why? (top SHAP drivers)")
    st.dataframe(
        pd.DataFrame(
            [
                {
                    "feature": d.feature,
                    "value": d.value,
                    "shap": round(d.shap_value, 4),
                    "effect": d.direction,
                }
                for d in drivers
            ]
        ),
        use_container_width=True,
        hide_index=True,
    )

    st.subheader("Recommended retention actions")
    for a in actions:
        with st.container(border=True):
            st.markdown(f"**{a.title}**  ·  _priority: {a.priority}_")
            st.write(a.detail)

    st.divider()
    if st.button("💾 Save this prediction to the database"):
        try:
            record_to_save = PredictionRecord(
                customer_id=state["record"]["customer_id"],
                churn_probability=prediction.probability,
                risk_band=prediction.risk_band,
                model_algorithm=state["algorithm"],
                top_drivers=[
                    {"feature": d.feature, "shap_value": d.shap_value, "direction": d.direction}
                    for d in drivers
                ],
                actor="predict-ui",
            )
            new_id = save_prediction(record_to_save)
            st.success(f"Saved prediction #{new_id}.")
        except Exception as exc:  # noqa: BLE001 - surface DB/domain errors gracefully
            st.error(f"Could not save prediction (is the database reachable?): {exc}")
