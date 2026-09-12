"""Shared pytest fixtures for the fraud detection test suite.

Adds ``src/`` to ``sys.path`` so the suite runs even without
``PYTHONPATH=src``. Every test gets an isolated SQLite database and its own
copy of a real trained model store (trained once per session), with all
cached singletons reset between tests.
"""

from __future__ import annotations

import shutil
import sys
from collections.abc import Iterator
from pathlib import Path

import pytest

_SRC = Path(__file__).resolve().parents[1] / "src"
if str(_SRC) not in sys.path:
    sys.path.insert(0, str(_SRC))

from fastapi import FastAPI  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from sqlalchemy.orm import Session  # noqa: E402

from fraud_detection.api import deps  # noqa: E402
from fraud_detection.core.config import Settings, get_settings  # noqa: E402
from fraud_detection.db import session as db_session_module  # noqa: E402


def pytest_configure(config: pytest.Config) -> None:
    """Register custom markers so ``--strict-markers`` runs stay green."""
    config.addinivalue_line("markers", "unit: fast, isolated unit tests")
    config.addinivalue_line("markers", "integration: API-level tests via TestClient")
    config.addinivalue_line("markers", "model: ML model quality and invariant tests")


# --------------------------------------------------------------------------
# The globally installed pytest-flask plugin ships autouse fixtures that grab
# any fixture named ``app`` and treat it as a Flask app. Override them with
# no-ops so our FastAPI ``app`` fixture is left alone.
# --------------------------------------------------------------------------


@pytest.fixture(autouse=True)
def _monkeypatch_response_class() -> None:
    """No-op override of the pytest-flask autouse fixture."""


@pytest.fixture(autouse=True)
def _push_request_context() -> None:
    """No-op override of the pytest-flask autouse fixture."""


@pytest.fixture(autouse=True)
def _configure_application() -> None:
    """No-op override of the pytest-flask autouse fixture."""


def _reset_state() -> None:
    """Reset settings, cached singletons and the DB engine between tests."""
    get_settings.cache_clear()
    deps.reset_singletons()
    db_session_module.reset_engine()


@pytest.fixture(scope="session")
def trained_model_dir(tmp_path_factory: pytest.TempPathFactory) -> Path:
    """Train one real model into a session-scoped local model store."""
    from fraud_detection.ml.train import train_pipeline

    target = tmp_path_factory.mktemp("model-store")
    summary = train_pipeline(model_dir=target, n_samples=4000, seed=42)
    assert summary["gates_passed"] is True
    return target


@pytest.fixture()
def model_dir(tmp_path: Path, trained_model_dir: Path) -> Path:
    """Per-test mutable copy of the trained model store."""
    target = tmp_path / "models"
    shutil.copytree(trained_model_dir, target)
    return target


@pytest.fixture()
def settings(
    tmp_path: Path, model_dir: Path, monkeypatch: pytest.MonkeyPatch
) -> Iterator[Settings]:
    """Isolated test settings: fresh SQLite DB + private model store."""
    db_path = tmp_path / "test.db"
    monkeypatch.setenv("FRAUD_DATABASE_URL", "sqlite:///" + db_path.as_posix())
    monkeypatch.setenv("FRAUD_MODEL_DIR", str(model_dir))
    monkeypatch.setenv("FRAUD_ENVIRONMENT", "test")
    monkeypatch.setenv("FRAUD_DEBUG", "false")
    monkeypatch.setenv("FRAUD_AB_TEST_ENABLED", "true")
    monkeypatch.setenv("FRAUD_AB_TRAFFIC_SPLIT", "10")
    monkeypatch.setenv("FRAUD_DRIFT_PSI_THRESHOLD", "0.2")
    monkeypatch.setenv("FRAUD_DRIFT_MIN_ROWS", "10")
    monkeypatch.setenv("FRAUD_DRIFT_WINDOW_SIZE", "500")
    monkeypatch.setenv("FRAUD_TRAINING_SAMPLES", "3000")
    monkeypatch.setenv("FRAUD_RETRAIN_MIN_INTERVAL_MINUTES", "60")
    monkeypatch.setenv("FRAUD_METRICS_ENABLED", "true")
    monkeypatch.setenv("FRAUD_API_PREFIX", "/api/v1")
    _reset_state()
    yield get_settings()
    _reset_state()


@pytest.fixture()
def db_session(settings: Settings) -> Iterator[Session]:
    """A live SQLAlchemy session against the per-test SQLite database."""
    db_session_module.init_db()
    session = db_session_module.get_session_factory()()
    yield session
    session.rollback()
    session.close()


@pytest.fixture()
def app(settings: Settings) -> FastAPI:
    """Return a freshly built FastAPI application using the test settings."""
    from fraud_detection.main import create_app

    return create_app()


@pytest.fixture()
def client(app: FastAPI) -> Iterator[TestClient]:
    """Return a TestClient with the app lifespan running."""
    with TestClient(app) as test_client:
        yield test_client


def make_prediction_payload(index: int = 0, **overrides: object) -> dict[str, object]:
    """Build a valid prediction request payload for tests."""
    payload: dict[str, object] = {
        "transaction_id": f"txn-{index}",
        "account_id": f"acct-{index % 7}",
        "amount": 50.0 + index * 13.3,
        "merchant_category": ["grocery", "electronics", "gambling"][index % 3],
        "timestamp": "2026-07-12T10:00:00Z",
    }
    payload.update(overrides)
    return payload
