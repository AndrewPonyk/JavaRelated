"""Unit tests for the sticky champion/challenger router."""

from __future__ import annotations

from datetime import UTC, datetime
from unittest.mock import MagicMock

import pytest

from fraud_detection.db.models import ABAssignment
from fraud_detection.db.repositories import ABAssignmentRepository
from fraud_detection.monitoring.metrics import FRAUD_DB_ERRORS_TOTAL
from fraud_detection.services.ab_config import ABConfigStore
from fraud_detection.services.ab_router import ABRouter, hash_bucket

pytestmark = pytest.mark.unit


def _router(enabled: bool = True, split: int = 10) -> ABRouter:
    store = ABConfigStore()
    store.update(enabled=enabled, traffic_split=split)
    return ABRouter(config_store=store)


def test_hash_bucket_is_deterministic_and_bounded(settings) -> None:
    for account in ("a", "acct-42", "x" * 64):
        first = hash_bucket(account)
        assert first == hash_bucket(account)
        assert 0 <= first < 100


def test_same_account_always_gets_same_variant(settings) -> None:
    router = _router(split=50)
    variants = {router.assign_variant("acct-stable") for _ in range(20)}
    assert len(variants) == 1


def test_disabled_experiment_routes_everything_to_champion(settings) -> None:
    router = _router(enabled=False, split=100)
    assert all(router.assign_variant(f"acct-{i}") == "champion" for i in range(50))


def test_split_zero_routes_everything_to_champion(settings) -> None:
    router = _router(split=0)
    assert all(router.assign_variant(f"acct-{i}") == "champion" for i in range(100))


def test_split_hundred_routes_everything_to_challenger(settings) -> None:
    router = _router(split=100)
    assert all(router.assign_variant(f"acct-{i}") == "challenger" for i in range(100))


def test_split_distribution_is_roughly_honoured(settings) -> None:
    router = _router(split=30)
    assigned = sum(router.assign_variant(f"account-{i}") == "challenger" for i in range(2000))
    assert 0.25 <= assigned / 2000 <= 0.35


def test_sticky_db_assignment_wins_over_hash(settings, db_session) -> None:
    router = _router(split=0)  # hash alone would always say champion
    account = "acct-sticky"
    db_session.add(
        ABAssignment(
            account_id=account,
            variant="challenger",
            model_version="1",
            assigned_at=datetime.now(UTC),
        )
    )
    db_session.flush()
    assert router.assign_variant(account, db_session) == "challenger"


def test_db_failure_falls_back_to_hash(settings) -> None:
    router = _router(split=100)
    broken_session = MagicMock()
    broken_session.scalars.side_effect = RuntimeError("db down")
    before = FRAUD_DB_ERRORS_TOTAL._value.get()
    assert router.assign_variant("acct-db-down", broken_session) == "challenger"
    assert FRAUD_DB_ERRORS_TOTAL._value.get() == before + 1


def test_record_assignment_is_idempotent(settings, db_session) -> None:
    router = _router()
    router.record_assignment("acct-once", "champion", "1", db_session)
    router.record_assignment("acct-once", "champion", "1", db_session)
    repo = ABAssignmentRepository(db_session)
    assert repo.get_by_account("acct-once") is not None
    rows = db_session.query(ABAssignment).filter_by(account_id="acct-once").all()
    assert len(rows) == 1
