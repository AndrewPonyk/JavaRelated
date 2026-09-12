"""Shared pytest fixtures."""

import pytest
from fastapi.testclient import TestClient

from app.core.security import create_access_token
from app.main import create_app


@pytest.fixture
def app():
    """A fresh app per test (isolates dependency overrides + middleware state)."""
    return create_app()


@pytest.fixture
def client(app):
    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture
def token():
    def _make(scopes=("pharmacy:check", "admin"), subject="tester") -> str:
        return create_access_token(subject, {"scope": " ".join(scopes)})

    return _make


@pytest.fixture
def admin_headers(token):
    return {"Authorization": f"Bearer {token(scopes=['admin'])}"}
