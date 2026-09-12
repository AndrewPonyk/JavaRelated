"""Shared pytest fixtures.

Uses the synthetic data generator so tests never touch real customer data. DB
fixtures self-skip when PostgreSQL is unreachable, so the unit suite runs anywhere
while integration tests run in CI (which provides a PostgreSQL service).
"""

from __future__ import annotations

import pandas as pd
import pytest

from churn_predictor.data.synthetic import generate_synthetic_customers


@pytest.fixture(scope="session")
def synthetic_customers() -> pd.DataFrame:
    """A small, schema-valid synthetic dataset with a learnable churn signal."""
    return generate_synthetic_customers(600, seed=42)


@pytest.fixture(scope="session")
def trained_artifact(tmp_path_factory, synthetic_customers: pd.DataFrame) -> str:
    """Train a small model once per session and return the artifact path.

    Used by slow tests for predict/explain. Single algorithm + few trials keeps it
    fast while still exercising the full train → persist → load → score path.
    """
    from churn_predictor.models.train import train_model

    path = tmp_path_factory.mktemp("model") / "model.joblib"
    train_model(
        synthetic_customers,
        algorithms=("xgboost",),
        artifact_path=str(path),
        n_trials=3,
    )
    return str(path)


def _db_reachable() -> bool:
    try:
        from sqlalchemy import text

        from churn_predictor.db.connection import get_engine

        with get_engine().connect() as conn:
            conn.execute(text("SELECT 1"))
        return True
    except Exception:  # noqa: BLE001
        return False


@pytest.fixture(scope="session")
def db_engine():
    """Provide a migrated engine, or skip the test if PostgreSQL is unavailable."""
    if not _db_reachable():
        pytest.skip("PostgreSQL not reachable; skipping integration test")

    from churn_predictor.db.connection import get_engine
    from churn_predictor.db.migrate import apply_migrations

    engine = get_engine()
    apply_migrations(engine)
    return engine
