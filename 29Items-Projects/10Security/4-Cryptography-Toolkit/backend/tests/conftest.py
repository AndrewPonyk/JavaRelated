"""Pytest fixtures — in-memory SQLite app + client for integration tests."""

import pytest

from crypto_toolkit.app import create_app
from crypto_toolkit.extensions import db as _db

ADMIN = {"email": "admin@toolkit.test", "password": "super-secret-1"}
STUDENT = {"email": "student@toolkit.test", "password": "student-pass-1"}


@pytest.fixture(scope="session")
def app():
    app = create_app("testing")
    with app.app_context():
        _db.create_all()
        # Bootstrap accounts BEFORE any test creates users, so the documented
        # "first registered user becomes admin" rule lands on ADMIN.
        with app.test_client() as c:
            first = c.post("/api/auth/register", json=ADMIN)
            assert first.status_code == 201, first.get_json()
            assert first.get_json()["data"]["user"]["is_admin"] is True
            second = c.post("/api/auth/register", json=STUDENT)
            assert second.status_code == 201, second.get_json()
            assert second.get_json()["data"]["user"]["is_admin"] is False
        yield app
        _db.session.remove()
        _db.drop_all()


@pytest.fixture()
def client(app):
    return app.test_client()


def _login(c: object, creds: dict) -> dict:
    res = c.post("/api/auth/login", json=creds)
    assert res.status_code == 200, res.get_json()
    return {"Authorization": f'Bearer {res.get_json()["data"]["token"]}'}


@pytest.fixture(scope="session")
def admin_headers(app):
    with app.test_client() as c:
        return _login(c, ADMIN)


@pytest.fixture(scope="session")
def user_headers(app):
    with app.test_client() as c:
        return _login(c, STUDENT)
