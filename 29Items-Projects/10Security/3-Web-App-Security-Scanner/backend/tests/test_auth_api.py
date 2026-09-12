"""API tests: register/login/refresh/me + API key lifecycle (real JWTs)."""

from __future__ import annotations

from tests.conftest import auth_headers, make_user


class TestRegister:
    async def test_register_creates_viewer(self, client):
        resp = await client.post(
            "/api/v1/auth/register",
            json={
                "email": "newbie@test.dev",
                "password": "long-enough-pw-1",
                "full_name": "New Bee",
            },
        )
        assert resp.status_code == 201
        body = resp.json()
        assert body["email"] == "newbie@test.dev"
        assert body["role"] == "viewer"
        assert "password" not in body and "hashed_password" not in body

    async def test_duplicate_email_409(self, client):
        payload = {"email": "dup@test.dev", "password": "long-enough-pw-1"}
        assert (await client.post("/api/v1/auth/register", json=payload)).status_code == 201
        resp = await client.post("/api/v1/auth/register", json=payload)
        assert resp.status_code == 409
        assert resp.json()["error"]["code"] == "http_409"

    async def test_bad_email_and_short_password_422(self, client):
        resp = await client.post(
            "/api/v1/auth/register",
            json={
                "email": "not-an-email",
                "password": "long-enough-pw-1",
            },
        )
        assert resp.status_code == 422
        resp = await client.post(
            "/api/v1/auth/register",
            json={
                "email": "ok@test.dev",
                "password": "short",
            },
        )
        assert resp.status_code == 422
        assert resp.json()["error"]["code"] == "validation_error"


class TestLogin:
    async def test_login_returns_token_pair(self, client, session_factory):
        await make_user(session_factory, "login@test.dev", "scanner", "password-123")
        resp = await client.post(
            "/api/v1/auth/login",
            json={
                "email": "login@test.dev",
                "password": "password-123",
            },
        )
        assert resp.status_code == 200
        body = resp.json()
        assert body["token_type"] == "bearer"
        assert body["access_token"] and body["refresh_token"]
        assert body["expires_in"] > 0

    async def test_wrong_password_401_generic(self, client, session_factory):
        await make_user(session_factory, "login@test.dev")
        resp = await client.post(
            "/api/v1/auth/login",
            json={
                "email": "login@test.dev",
                "password": "wrong-password",
            },
        )
        assert resp.status_code == 401
        assert resp.json()["error"]["message"] == "Invalid email or password"

    async def test_unknown_email_same_message(self, client):
        resp = await client.post(
            "/api/v1/auth/login",
            json={
                "email": "ghost@test.dev",
                "password": "whatever-xyz",
            },
        )
        assert resp.status_code == 401
        assert resp.json()["error"]["message"] == "Invalid email or password"

    async def test_disabled_user_403(self, client, session_factory):
        await make_user(session_factory, "off@test.dev", is_active=False)
        resp = await client.post(
            "/api/v1/auth/login",
            json={
                "email": "off@test.dev",
                "password": "password-123",
            },
        )
        assert resp.status_code == 403


class TestRefresh:
    async def test_refresh_rotates_tokens(self, client, session_factory):
        await make_user(session_factory, "r@test.dev", "viewer")
        login = await client.post(
            "/api/v1/auth/login",
            json={
                "email": "r@test.dev",
                "password": "password-123",
            },
        )
        refresh_token = login.json()["refresh_token"]
        resp = await client.post("/api/v1/auth/refresh", json={"refresh_token": refresh_token})
        assert resp.status_code == 200
        assert resp.json()["access_token"] != login.json()["access_token"]

    async def test_access_token_rejected_as_refresh(self, client, session_factory):
        user = await make_user(session_factory, "r@test.dev")
        token = auth_headers(user)["Authorization"].split(" ", 1)[1]
        resp = await client.post("/api/v1/auth/refresh", json={"refresh_token": token})
        assert resp.status_code == 401

    async def test_garbage_refresh_401(self, client):
        resp = await client.post("/api/v1/auth/refresh", json={"refresh_token": "junk-junk-junk"})
        assert resp.status_code == 401


class TestMe:
    async def test_me_with_jwt(self, client, session_factory):
        user = await make_user(session_factory, "me@test.dev", "scanner")
        resp = await client.get("/api/v1/auth/me", headers=auth_headers(user))
        assert resp.status_code == 200
        assert resp.json()["email"] == "me@test.dev"

    async def test_me_without_token_401(self, client):
        resp = await client.get("/api/v1/auth/me")
        assert resp.status_code == 401
        assert resp.json()["error"]["code"] == "http_401"


class TestApiKeys:
    async def test_issue_list_revoke_and_authenticate(self, client, session_factory):
        owner = await make_user(session_factory, "ci@test.dev", "scanner")
        headers = auth_headers(owner)

        resp = await client.post(
            "/api/v1/auth/api-keys",
            headers=headers,
            json={
                "name": "ci-pipeline",
                "role": "scanner",
            },
        )
        assert resp.status_code == 201
        key = resp.json()
        assert key["secret"].startswith("wss_")
        assert key["prefix"] in key["secret"]

        listed = await client.get("/api/v1/auth/api-keys", headers=headers)
        assert listed.status_code == 200
        assert [k["id"] for k in listed.json()] == [key["id"]]
        assert all("secret" not in k or k["secret"] is None for k in listed.json())

        # the issued key authenticates as a scanner
        key_headers = {"Authorization": f"Bearer {key['secret']}"}
        scans = await client.get("/api/v1/scans", headers=key_headers)
        assert scans.status_code == 200

        revoked = await client.delete(f"/api/v1/auth/api-keys/{key['id']}", headers=headers)
        assert revoked.status_code == 204
        scans = await client.get("/api/v1/scans", headers=key_headers)
        assert scans.status_code == 401  # revoked → rejected

    async def test_viewer_cannot_issue(self, client, session_factory):
        viewer = await make_user(session_factory, "v@test.dev", "viewer")
        resp = await client.post(
            "/api/v1/auth/api-keys",
            headers=auth_headers(viewer),
            json={
                "name": "nope",
            },
        )
        assert resp.status_code == 403

    async def test_cannot_escalate_role(self, client, session_factory):
        scanner = await make_user(session_factory, "s@test.dev", "scanner")
        resp = await client.post(
            "/api/v1/auth/api-keys",
            headers=auth_headers(scanner),
            json={
                "name": "escalate",
                "role": "admin",
            },
        )
        assert resp.status_code == 403

    async def test_expired_key_rejected(self, client, session_factory, monkeypatch):
        from datetime import UTC, datetime, timedelta

        from app.core.security import generate_api_key
        from app.models.api_key import ApiKey

        owner = await make_user(session_factory, "o@test.dev", "admin")
        secret, prefix, secret_hash = generate_api_key()
        async with session_factory() as session:
            session.add(
                ApiKey(
                    name="old",
                    prefix=prefix,
                    secret_hash=secret_hash,
                    role="viewer",
                    created_by=owner.id,
                    expires_at=datetime.now(UTC) - timedelta(days=1),
                )
            )
            await session.commit()

        resp = await client.get("/api/v1/scans", headers={"Authorization": f"Bearer {secret}"})
        assert resp.status_code == 401

    async def test_scoped_list_hides_others_keys(self, client, session_factory):
        a = await make_user(session_factory, "a@test.dev", "scanner")
        b = await make_user(session_factory, "b@test.dev", "scanner")
        created = await client.post(
            "/api/v1/auth/api-keys",
            headers=auth_headers(a),
            json={
                "name": "mine",
            },
        )
        listed = await client.get("/api/v1/auth/api-keys", headers=auth_headers(b))
        assert listed.json() == []

        admin = await make_user(session_factory, "adm@test.dev", "admin")
        admin_list = await client.get("/api/v1/auth/api-keys", headers=auth_headers(admin))
        assert [k["id"] for k in admin_list.json()] == [created.json()["id"]]

    async def test_cannot_revoke_foreign_key(self, client, session_factory):
        a = await make_user(session_factory, "a@test.dev", "scanner")
        b = await make_user(session_factory, "b@test.dev", "scanner")
        created = await client.post(
            "/api/v1/auth/api-keys",
            headers=auth_headers(a),
            json={
                "name": "mine",
            },
        )
        resp = await client.delete(
            f"/api/v1/auth/api-keys/{created.json()['id']}", headers=auth_headers(b)
        )
        assert resp.status_code == 403
