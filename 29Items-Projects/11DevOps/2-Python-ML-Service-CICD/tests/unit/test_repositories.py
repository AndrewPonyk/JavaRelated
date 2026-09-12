"""Unit tests for the CRUD repositories (real SQLite roundtrips)."""

from __future__ import annotations

import uuid
from datetime import UTC, datetime, timedelta
from decimal import Decimal

import pytest

from fraud_detection.db.models import ABAssignment, DriftReport, ModelVersion, Prediction
from fraud_detection.db.repositories import (
    ABAssignmentRepository,
    DriftReportRepository,
    ModelVersionRepository,
    PredictionRepository,
)

pytestmark = pytest.mark.unit

NOW = datetime(2026, 7, 12, 12, 0, 0, tzinfo=UTC)


def _prediction(index: int, account: str = "acct-1") -> Prediction:
    return Prediction(
        id=uuid.uuid4(),
        transaction_id=f"txn-{index}",
        account_id=account,
        amount=Decimal("100.00"),
        model_version="1",
        variant="champion",
        fraud_probability=0.25,
        is_fraud=False,
        latency_ms=1.5,
        features={"log_amount": 4.6, "hour_of_day": 12.0, "merchant_category_hash": 0.5},
        created_at=NOW + timedelta(seconds=index),
    )


def test_prediction_crud_and_pagination(db_session) -> None:
    repo = PredictionRepository(db_session)
    for i in range(5):
        repo.add(_prediction(i, account="acct-a" if i < 3 else "acct-b"))

    assert repo.get_by_transaction_id("txn-2") is not None
    assert repo.get_by_transaction_id("missing") is None

    items, total = repo.list(limit=2, offset=0)
    assert total == 5 and len(items) == 2
    assert items[0].transaction_id == "txn-4"  # newest first

    items, total = repo.list(limit=10, offset=0, account_id="acct-a")
    assert total == 3 and all(item.account_id == "acct-a" for item in items)

    recent = repo.list_recent(3)
    assert [item.transaction_id for item in recent] == ["txn-4", "txn-3", "txn-2"]


def test_model_version_crud(db_session) -> None:
    repo = ModelVersionRepository(db_session)
    repo.add(
        ModelVersion(
            name="fraud-detection", version="1", stage="champion", auc=0.9, registered_at=NOW
        )
    )
    repo.add(
        ModelVersion(
            name="fraud-detection",
            version="2",
            stage="challenger",
            auc=0.92,
            registered_at=NOW + timedelta(minutes=1),
        )
    )

    assert repo.get_by_version("1").stage == "champion"
    assert [row.version for row in repo.list()] == ["2", "1"]

    assert repo.update_stage("2", "champion").stage == "champion"
    assert repo.update_stage("missing", "champion") is None

    assert repo.delete("1") is True
    assert repo.delete("1") is False
    assert repo.get_by_version("1") is None


def test_ab_assignment_repository(db_session) -> None:
    repo = ABAssignmentRepository(db_session)
    assert repo.get_by_account("acct-1") is None
    repo.add(
        ABAssignment(account_id="acct-1", variant="challenger", model_version="2", assigned_at=NOW)
    )
    found = repo.get_by_account("acct-1")
    assert found is not None and found.variant == "challenger"


def test_drift_report_repository(db_session) -> None:
    repo = DriftReportRepository(db_session)
    repo.add_many(
        [
            DriftReport(
                feature_name=f"feature-{i}",
                psi_score=0.1 * i,
                threshold=0.2,
                drift_detected=i >= 2,
                window_start=NOW - timedelta(hours=1),
                window_end=NOW,
                created_at=NOW + timedelta(seconds=i),
            )
            for i in range(4)
        ]
    )
    items = repo.list(limit=2)
    assert len(items) == 2
    assert items[0].feature_name == "feature-3"  # newest first
