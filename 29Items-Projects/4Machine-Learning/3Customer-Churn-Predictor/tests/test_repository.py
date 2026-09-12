"""Integration tests for the repository layer (require a live PostgreSQL).

These self-skip when PostgreSQL is unreachable (see the ``db_engine`` fixture).
In CI a PostgreSQL service is provided, so they run there.
"""

from __future__ import annotations

import uuid

import pytest

from churn_predictor.db import repository as repo
from churn_predictor.db.repository import Customer, PredictionRecord

pytestmark = pytest.mark.integration


@pytest.fixture
def sample_customer() -> Customer:
    return Customer(
        customer_id=f"TEST-{uuid.uuid4().hex[:8]}",
        tenure_months=10,
        monthly_charges=55.0,
        total_charges=550.0,
        contract_type="One year",
        payment_method="Credit card",
        num_support_tickets=1,
        is_churned=False,
    )


def test_customer_crud_roundtrip(db_engine, sample_customer: Customer) -> None:
    cid = repo.create_customer(sample_customer)
    try:
        fetched = repo.get_customer(cid)
        assert fetched is not None
        assert fetched.tenure_months == 10

        sample_customer.tenure_months = 24
        assert repo.update_customer(sample_customer) is True
        assert repo.get_customer(cid).tenure_months == 24
    finally:
        assert repo.delete_customer(cid) is True
        assert repo.get_customer(cid) is None


def test_bulk_upsert_and_count(db_engine) -> None:
    customers = [
        Customer(
            customer_id=f"BULK-{i}-{uuid.uuid4().hex[:6]}", tenure_months=i, monthly_charges=20.0
        )
        for i in range(5)
    ]
    inserted = repo.upsert_customers_bulk(customers)
    assert inserted == 5
    assert repo.count_customers() >= 5
    for c in customers:
        repo.delete_customer(c.customer_id)


def test_save_and_read_prediction(db_engine) -> None:
    record = PredictionRecord(
        customer_id="TEST-PRED",
        churn_probability=0.73,
        risk_band="high",
        model_algorithm="xgboost",
        top_drivers=[
            {"feature": "num_support_tickets", "shap_value": 0.4, "direction": "increases"}
        ],
        actor="pytest",
    )
    new_id = repo.save_prediction(record)
    assert new_id > 0
    recent = repo.recent_predictions(limit=10)
    assert any(r["id"] == new_id for r in recent)


def test_save_model_run(db_engine) -> None:
    meta = {
        "algorithm": "lightgbm",
        "metrics": {"holdout_roc_auc": 0.81},
        "params": {"n_estimators": 300},
        "feature_columns": ["tenure_months", "monthly_charges"],
        "trained_at": "2026-06-14T00:00:00+00:00",
    }
    run_id = repo.save_model_run(meta)
    assert run_id > 0
    latest = repo.latest_model_run()
    assert latest is not None
    assert latest["algorithm"] == "lightgbm"
