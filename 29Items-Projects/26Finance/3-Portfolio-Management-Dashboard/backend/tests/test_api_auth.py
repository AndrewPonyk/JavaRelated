from __future__ import annotations

import uuid


def test_register_and_me(client):
    email = f"u_{uuid.uuid4().hex[:8]}@example.com"
    reg = client.post(
        "/api/v1/auth/register",
        json={"email": email, "password": "password123", "full_name": "Jane"},
    )
    assert reg.status_code == 201
    assert reg.json()["email"] == email

    login = client.post("/api/v1/auth/login", data={"username": email, "password": "password123"})
    assert login.status_code == 200
    token = login.json()["access_token"]

    me = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert me.status_code == 200
    assert me.json()["email"] == email
    assert me.json()["full_name"] == "Jane"


def test_duplicate_email_rejected(client):
    email = f"dup_{uuid.uuid4().hex[:8]}@example.com"
    payload = {"email": email, "password": "password123"}
    assert client.post("/api/v1/auth/register", json=payload).status_code == 201
    dup = client.post("/api/v1/auth/register", json=payload)
    assert dup.status_code == 422
    assert dup.json()["error"]["code"] == "validation_error"


def test_login_wrong_password(client):
    email = f"wp_{uuid.uuid4().hex[:8]}@example.com"
    client.post("/api/v1/auth/register", json={"email": email, "password": "password123"})
    bad = client.post("/api/v1/auth/login", data={"username": email, "password": "nope"})
    assert bad.status_code == 401


def test_short_password_validation(client):
    resp = client.post(
        "/api/v1/auth/register",
        json={"email": "x@example.com", "password": "short"},
    )
    assert resp.status_code == 422  # FastAPI request validation


def test_protected_route_requires_token(client):
    assert client.get("/api/v1/auth/me").status_code == 401
    assert client.get("/api/v1/portfolios").status_code == 401
