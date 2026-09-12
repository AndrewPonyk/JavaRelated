"""Unit tests for the prediction service (real local model + SQLite)."""

from __future__ import annotations

from datetime import UTC, datetime
from unittest.mock import MagicMock

import pytest

from fraud_detection.db.repositories import ABAssignmentRepository, PredictionRepository
from fraud_detection.ml.features import FEATURE_COLUMNS
from fraud_detection.monitoring.metrics import FRAUD_DB_ERRORS_TOTAL
from fraud_detection.schemas.prediction import PredictionRequest, PredictionResponse
from fraud_detection.services.ab_config import ABConfigStore
from fraud_detection.services.ab_router import ABRouter
from fraud_detection.services.model_loader import ModelLoader
from fraud_detection.services.prediction_service import PredictionService

pytestmark = pytest.mark.unit


def _service() -> PredictionService:
    return PredictionService(
        ab_router=ABRouter(config_store=ABConfigStore()), model_loader=ModelLoader()
    )


def _request(
    txn: str = "txn-1", account: str = "acct-1", amount: float = 150.0
) -> PredictionRequest:
    return PredictionRequest(
        transaction_id=txn,
        account_id=account,
        amount=amount,
        merchant_category="electronics",
        timestamp=datetime(2026, 7, 12, 14, tzinfo=UTC),
    )


def test_predict_works_without_a_session(settings) -> None:
    response = _service().predict(_request())
    assert isinstance(response, PredictionResponse)
    assert response.variant in ("champion", "challenger")
    assert response.model_version == "1"
    assert 0.0 <= response.fraud_probability <= 1.0
    assert response.latency_ms >= 0.0
    assert response.is_fraud == (response.fraud_probability >= 0.5)


def test_predict_persists_row_and_sticky_assignment(settings, db_session) -> None:
    response = _service().predict(_request(txn="txn-persist", account="acct-p"), db_session)
    db_session.commit()

    record = PredictionRepository(db_session).get_by_transaction_id("txn-persist")
    assert record is not None
    assert record.features is not None
    assert set(FEATURE_COLUMNS) <= set(record.features)
    assert record.variant == response.variant
    assert float(record.fraud_probability) == pytest.approx(response.fraud_probability)

    assignment = ABAssignmentRepository(db_session).get_by_account("acct-p")
    assert assignment is not None and assignment.variant == response.variant


def test_duplicate_transaction_id_still_returns_score(settings, db_session) -> None:
    service = _service()
    service.predict(_request(txn="txn-dup"), db_session)
    db_session.commit()
    before = FRAUD_DB_ERRORS_TOTAL._value.get()
    response = service.predict(_request(txn="txn-dup"), db_session)
    assert 0.0 <= response.fraud_probability <= 1.0
    assert FRAUD_DB_ERRORS_TOTAL._value.get() == before + 1


def test_database_outage_still_returns_score(settings) -> None:
    broken_session = MagicMock()
    broken_session.scalars.side_effect = RuntimeError("db down")
    broken_session.add.side_effect = RuntimeError("db down")
    # The mocked SAVEPOINT context manager must propagate exceptions
    # (a bare MagicMock __exit__ returns a truthy mock and would swallow them).
    broken_session.begin_nested.return_value.__enter__.return_value = None
    broken_session.begin_nested.return_value.__exit__.return_value = False
    before = FRAUD_DB_ERRORS_TOTAL._value.get()
    response = _service().predict(_request(txn="txn-outage"), broken_session)
    assert 0.0 <= response.fraud_probability <= 1.0
    assert FRAUD_DB_ERRORS_TOTAL._value.get() > before
