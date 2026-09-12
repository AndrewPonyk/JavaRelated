"""Integration test for the migration runner (requires a live PostgreSQL)."""

from __future__ import annotations

import pytest
from sqlalchemy import text

from churn_predictor.db.migrate import apply_migrations

pytestmark = pytest.mark.integration


def test_migrations_are_idempotent(db_engine) -> None:
    # db_engine already applied migrations once; applying again must not error.
    applied = apply_migrations(db_engine)
    assert any("initial" in name for name in applied)

    with db_engine.connect() as conn:
        for table in ("customers", "predictions", "model_runs"):
            exists = conn.execute(text("SELECT to_regclass(:t)"), {"t": table}).scalar_one()
            assert exists is not None, f"table {table} missing"
