"""Unit tests for PSI computation and the DB-backed drift evaluation."""

from __future__ import annotations

import uuid
from datetime import UTC, datetime, timedelta
from decimal import Decimal

import numpy as np
import pytest

from fraud_detection.api import deps
from fraud_detection.core.config import get_settings
from fraud_detection.db.models import Prediction
from fraud_detection.db.repositories import DriftReportRepository
from fraud_detection.ml.features import FEATURE_COLUMNS, build_features
from fraud_detection.ml.train import compute_baseline, generate_synthetic_transactions
from fraud_detection.services.drift_detector import (
    DriftDetector,
    population_stability_index,
    psi_from_baseline,
)

pytestmark = pytest.mark.unit

NOW = datetime(2026, 7, 12, 12, 0, 0, tzinfo=UTC)


class FakeRetraining:
    """Records submit calls; behaves as an always-available backend."""

    def __init__(self) -> None:
        self.calls: list[tuple[str, bool]] = []

    def submit(self, reason: str, debounce: bool = False) -> object:
        self.calls.append((reason, debounce))
        return object()


def _insert_predictions(session, feature_rows: list[dict[str, float]]) -> None:
    for i, features in enumerate(feature_rows):
        session.add(
            Prediction(
                id=uuid.uuid4(),
                transaction_id=f"drift-{i}",
                account_id=f"acct-{i % 5}",
                amount=Decimal("100.00"),
                model_version="1",
                variant="champion",
                fraud_probability=0.2,
                is_fraud=False,
                latency_ms=1.0,
                features=features,
                created_at=NOW + timedelta(seconds=i),
            )
        )
    session.flush()


def _training_like_features(n: int, seed: int = 42) -> list[dict[str, float]]:
    frame = build_features(generate_synthetic_transactions(n_samples=n, seed=seed))
    return [
        {column: float(row[column]) for column in FEATURE_COLUMNS} for _, row in frame.iterrows()
    ]


def test_psi_near_zero_for_identical_distributions() -> None:
    rng = np.random.default_rng(1)
    sample = rng.normal(0, 1, 5000)
    assert population_stability_index(sample, sample) < 0.01


def test_psi_flags_shifted_distribution() -> None:
    rng = np.random.default_rng(2)
    baseline = rng.normal(0, 1, 5000)
    shifted = rng.normal(1.5, 1, 5000)
    assert population_stability_index(baseline, shifted) > 0.2


def test_psi_rejects_empty_samples() -> None:
    with pytest.raises(ValueError):
        population_stability_index(np.array([]), np.array([1.0]))


def test_psi_from_baseline_matches_direction() -> None:
    frame = build_features(generate_synthetic_transactions(4000, seed=42))
    baseline = compute_baseline(frame[FEATURE_COLUMNS])
    same = frame["log_amount"].to_numpy()[:500]
    shifted = same + 3.0
    assert psi_from_baseline(baseline["log_amount"], same) < 0.1
    assert psi_from_baseline(baseline["log_amount"], shifted) > 0.2
    with pytest.raises(ValueError):
        psi_from_baseline(baseline["log_amount"], np.array([]))


def test_evaluate_reports_insufficient_data_without_rows(settings, db_session) -> None:
    summary = DriftDetector().evaluate(db_session)
    assert summary.status == "insufficient_data"
    assert summary.evaluated_rows == 0
    assert summary.retraining_triggered is False


def test_evaluate_ok_for_training_like_traffic(settings, db_session) -> None:
    # 400 fresh rows from the training distribution: expected same-dist PSI
    # is ~(bins-1)/n ~= 0.02, far below the 0.2 threshold.
    _insert_predictions(db_session, _training_like_features(400, seed=99))
    fake = FakeRetraining()
    summary = DriftDetector(retraining=fake).evaluate(db_session)
    assert summary.status == "ok"
    assert summary.evaluated_rows == 400
    assert len(summary.features) == len(FEATURE_COLUMNS)
    assert summary.retraining_triggered is False
    assert fake.calls == []


def test_evaluate_detects_drift_and_triggers_retraining(settings, db_session) -> None:
    rows = _training_like_features(100, seed=42)
    for row in rows:
        row["log_amount"] += 5.0  # strong covariate shift
    _insert_predictions(db_session, rows)

    fake = FakeRetraining()
    summary = DriftDetector(retraining=fake).evaluate(db_session)
    assert summary.status == "drift_detected"
    assert summary.retraining_triggered is True
    assert len(fake.calls) == 1
    reason, debounced = fake.calls[0]
    assert reason.startswith("drift:") and debounced is True

    reports = DriftReportRepository(db_session).list(limit=10)
    assert len(reports) == len(FEATURE_COLUMNS)


def test_auto_retrain_can_be_disabled(settings, db_session, monkeypatch) -> None:
    monkeypatch.setenv("FRAUD_AUTO_RETRAIN_ON_DRIFT", "false")
    get_settings.cache_clear()
    deps.reset_singletons()
    rows = _training_like_features(60, seed=42)
    for row in rows:
        row["log_amount"] += 5.0
    _insert_predictions(db_session, rows)

    fake = FakeRetraining()
    summary = DriftDetector(retraining=fake).evaluate(db_session)
    assert summary.status == "drift_detected"
    assert summary.retraining_triggered is False
    assert fake.calls == []
