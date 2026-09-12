"""API tests: admin user management (RBAC, self-lockout guards)."""

from __future__ import annotations

from tests.conftest import auth_headers, make_user


async def test_admin_lists_users(client, session_factory):
    admin = await make_user(session_factory, "admin@test.dev", "admin")
    await make_user(session_factory, "u1@test.dev", "viewer")
    resp = await client.get("/api/v1/users", headers=auth_headers(admin))
    assert resp.status_code == 200
    assert len(resp.json()) == 2


async def test_viewer_cannot_list_users(client, session_factory):
    viewer = await make_user(session_factory, "v@test.dev", "viewer")
    resp = await client.get("/api/v1/users", headers=auth_headers(viewer))
    assert resp.status_code == 403


async def test_promote_and_demote(client, session_factory):
    admin = await make_user(session_factory, "admin@test.dev", "admin")
    target = await make_user(session_factory, "t@test.dev", "viewer")

    resp = await client.patch(
        f"/api/v1/users/{target.id}", headers=auth_headers(admin), json={"role": "scanner"}
    )
    assert resp.status_code == 200 and resp.json()["role"] == "scanner"

    resp = await client.patch(
        f"/api/v1/users/{target.id}", headers=auth_headers(admin), json={"role": "viewer"}
    )
    assert resp.status_code == 200 and resp.json()["role"] == "viewer"


async def test_disable_user_blocks_login(client, session_factory):
    admin = await make_user(session_factory, "admin@test.dev", "admin")
    target = await make_user(session_factory, "t@test.dev", "viewer")
    resp = await client.patch(
        f"/api/v1/users/{target.id}", headers=auth_headers(admin), json={"is_active": False}
    )
    assert resp.status_code == 200 and resp.json()["is_active"] is False

    login = await client.post(
        "/api/v1/auth/login",
        json={
            "email": "t@test.dev",
            "password": "password-123",
        },
    )
    assert login.status_code == 403


async def test_admin_cannot_change_own_role_or_disable_self(client, session_factory):
    admin = await make_user(session_factory, "admin@test.dev", "admin")
    for payload in ({"role": "viewer"}, {"is_active": False}):
        resp = await client.patch(
            f"/api/v1/users/{admin.id}", headers=auth_headers(admin), json=payload
        )
        assert resp.status_code == 409


async def test_empty_patch_422(client, session_factory):
    admin = await make_user(session_factory, "admin@test.dev", "admin")
    resp = await client.patch(f"/api/v1/users/{admin.id}", headers=auth_headers(admin), json={})
    assert resp.status_code == 422


async def test_get_user_404(client, session_factory):
    admin = await make_user(session_factory, "admin@test.dev", "admin")
    resp = await client.get("/api/v1/users/999", headers=auth_headers(admin))
    assert resp.status_code == 404
