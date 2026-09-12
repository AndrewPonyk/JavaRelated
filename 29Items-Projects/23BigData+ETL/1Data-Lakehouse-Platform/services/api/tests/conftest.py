"""Shared API test fixtures — SQLite in-memory DB via dependency override,
so the suite needs no Postgres, Kafka, or Trino."""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.core import security
from app.core.config import get_settings
from app.db.models import Base
from app.db.session import get_db
from app.main import create_app
from app.services.audit_publisher import get_audit_publisher

VALID_DATASET = {
    "name": "sales.orders",
    "layer": "silver",
    "description": "Deduplicated order events",
    "owner_email": "data-eng@example.com",
    "s3_path": "s3://dev-lakehouse-silver/sales/orders",
}


@pytest.fixture(autouse=True)
def clean_caches():
    """Settings-derived singletons must not leak between tests."""
    for cached in (get_settings, security.get_token_verifier, get_audit_publisher):
        cached.cache_clear()
    yield
    for cached in (get_settings, security.get_token_verifier, get_audit_publisher):
        cached.cache_clear()


@pytest.fixture()
def make_client():
    """Factory so tests can adjust env/settings *before* the app is built."""

    def _make(overrides: dict | None = None) -> TestClient:
        engine = create_engine(
            "sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool
        )
        Base.metadata.create_all(engine)
        factory = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)

        app = create_app()

        def override_get_db():
            session = factory()
            try:
                yield session
            finally:
                session.close()

        app.dependency_overrides[get_db] = override_get_db
        for dependency, replacement in (overrides or {}).items():
            app.dependency_overrides[dependency] = replacement
        return TestClient(app)

    return _make


@pytest.fixture()
def client(make_client) -> TestClient:
    return make_client()


@pytest.fixture()
def valid_dataset() -> dict:
    return dict(VALID_DATASET)


@pytest.fixture()
def created_dataset(client) -> dict:
    response = client.post("/api/v1/datasets", json=VALID_DATASET)
    assert response.status_code == 201, response.text
    return response.json()
