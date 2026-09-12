"""Auth flow against a fake user store."""

import pytest

from app.core.security import hash_password
from app.repositories.users import UserRecord

PASSWORD = "s3cret-passphrase"


@pytest.fixture()
def fake_users(monkeypatch):
    users = {"andrii": UserRecord("andrii", hash_password(PASSWORD), ["admin"])}

    async def fake_get(username):
        return users.get(username)

    monkeypatch.setattr("app.repositories.users.get", fake_get)
    return users


def test_login_returns_token_and_roles(client, fake_users):
    resp = client.post("/api/v1/auth/token", json={"username": "andrii", "password": PASSWORD})
    assert resp.status_code == 200
    body = resp.json()
    assert body["token_type"] == "bearer"
    assert body["roles"] == ["admin"]
    assert body["expires_in"] > 0

    me = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {body['access_token']}"})
    assert me.status_code == 200
    assert me.json() == {"subject": "andrii", "roles": ["admin"]}


def test_wrong_password_is_401(client, fake_users):
    resp = client.post("/api/v1/auth/token", json={"username": "andrii", "password": "nope"})
    assert resp.status_code == 401
    assert resp.json()["error"]["code"] == "unauthorized"


def test_unknown_user_is_401_with_same_message(client, fake_users):
    resp = client.post("/api/v1/auth/token", json={"username": "ghost", "password": "nope"})
    assert resp.status_code == 401
    # Same message as wrong-password: no user enumeration.
    assert "Invalid username or password" in resp.json()["error"]["message"]


def test_empty_credentials_fail_validation(client, fake_users):
    resp = client.post("/api/v1/auth/token", json={"username": "", "password": ""})
    assert resp.status_code == 422


def test_me_rejects_garbage_token(client):
    resp = client.get("/api/v1/auth/me", headers={"Authorization": "Bearer garbage"})
    assert resp.status_code == 401


def test_expired_token_is_rejected(client, monkeypatch):
    from app.core.config import settings
    from app.core.security import create_access_token

    monkeypatch.setattr(settings, "access_token_expire_minutes", -5)
    stale = create_access_token("andrii", ["admin"])
    resp = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {stale}"})
    assert resp.status_code == 401
