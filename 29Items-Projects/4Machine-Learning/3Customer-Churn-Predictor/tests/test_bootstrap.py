"""Integration tests for the bootstrap helpers (require a live PostgreSQL)."""

from __future__ import annotations

import pytest

pytestmark = pytest.mark.integration

# Imported lazily inside tests: bootstrap pulls the training stack (optuna/boosters),
# which is only present in the full/CI environment, not the light unit lane.


def test_wait_for_db_returns_when_ready(db_engine) -> None:
    from churn_predictor import bootstrap

    # Should return promptly since the DB is up (db_engine fixture connected).
    bootstrap.wait_for_db(retries=3, delay=0.1)


def test_seed_if_empty_is_idempotent(db_engine) -> None:
    from churn_predictor import bootstrap
    from churn_predictor.db.repository import count_customers

    # First call may seed (if empty) or no-op; a second call must never re-seed.
    bootstrap.seed_if_empty(n=50)
    assert bootstrap.seed_if_empty(n=50) == 0
    assert count_customers() > 0
