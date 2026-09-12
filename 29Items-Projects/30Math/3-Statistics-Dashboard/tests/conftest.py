"""Shared fixtures: deterministic synthetic data + a SQLite-backed persistence env.

No test touches the network or the wall clock. The ``sqlite_env`` fixture routes
the whole persistence stack (settings → engine → repositories) at a temporary
SQLite file, so services are exercised for real without PostgreSQL; the
PostgreSQL-specific behavior stays in tests/integration.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd
import pytest

from app.core.config import Settings


@pytest.fixture()
def rng() -> np.random.Generator:
    return np.random.default_rng(42)


@pytest.fixture()
def ab_df(rng: np.random.Generator) -> pd.DataFrame:
    """Synthetic A/B frame with real effects: B converts better and has longer sessions."""
    n = 400
    return pd.DataFrame(
        {
            "user_id": np.arange(2 * n),
            "variant": np.repeat(["A", "B"], n),
            "converted": np.concatenate([rng.binomial(1, 0.10, n), rng.binomial(1, 0.18, n)]),
            "session_minutes": np.concatenate([rng.normal(12.0, 4.0, n), rng.normal(13.5, 4.0, n)]),
            "age_group": rng.choice(["18-24", "25-34", "35-44", "45+"], 2 * n),
        }
    )


@pytest.fixture()
def sqlite_env(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> Settings:
    """Point every persistence entry point at a temp SQLite database with schema applied."""
    from app.data import db as db_module
    from app.data.models import Base
    from app.services import (
        ab_testing_service,
        analysis_service,
        dataset_service,
        experiment_service,
    )

    settings = Settings(database_url=f"sqlite:///{tmp_path / 'test.db'}")

    # Each module bound get_settings at import time — patch every capture site.
    for module in (
        db_module,
        dataset_service,
        analysis_service,
        ab_testing_service,
        experiment_service,
    ):
        monkeypatch.setattr(module, "get_settings", lambda: settings)

    db_module.dispose_engine()
    Base.metadata.create_all(db_module.get_engine())
    yield settings
    db_module.dispose_engine()
