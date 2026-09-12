"""Auth API: login, register (admin-gated), and current principal."""

from __future__ import annotations

PREFIX = "/api/v1"


async def test_login_success(client):
    resp = await client.post(
        f"{PREFIX}/auth/token",
        data={"username": "rad@example.com", "password": "radpass123"},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["token_type"] == "bearer"
    assert body["access_token"]


async def test_login_bad_password(client):
    resp = await client.post(
        f"{PREFIX}/auth/token",
        data={"username": "rad@example.com", "password": "wrong"},
    )
    assert resp.status_code == 401


async def test_register_requires_admin(client, rad_headers):
    resp = await client.post(
        f"{PREFIX}/auth/register",
        headers=rad_headers,
        json={"email": "new@example.com", "password": "password1", "role": "technologist"},
    )
    assert resp.status_code == 403


async def test_admin_can_register(client, admin_headers):
    resp = await client.post(
        f"{PREFIX}/auth/register",
        headers=admin_headers,
        json={"email": "tech@example.com", "password": "password1", "role": "technologist"},
    )
    assert resp.status_code == 201
    assert resp.json()["email"] == "tech@example.com"

    # the new user can now log in
    login = await client.post(
        f"{PREFIX}/auth/token",
        data={"username": "tech@example.com", "password": "password1"},
    )
    assert login.status_code == 200


async def test_register_duplicate_rejected(client, admin_headers):
    body = {"email": "dup@example.com", "password": "password1"}
    first = await client.post(f"{PREFIX}/auth/register", headers=admin_headers, json=body)
    assert first.status_code == 201
    second = await client.post(f"{PREFIX}/auth/register", headers=admin_headers, json=body)
    assert second.status_code == 409  # ConflictError -> 409, not a 500


async def test_me_returns_principal(client, rad_headers):
    resp = await client.get(f"{PREFIX}/auth/me", headers=rad_headers)
    assert resp.status_code == 200
    assert resp.json()["sub"] == "rad@example.com"


async def test_register_validation_short_password(client, admin_headers):
    resp = await client.post(
        f"{PREFIX}/auth/register",
        headers=admin_headers,
        json={"email": "x@example.com", "password": "short"},
    )
    assert resp.status_code == 422
