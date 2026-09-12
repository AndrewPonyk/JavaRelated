"""Auth API integration tests — register/login, tokens, TOTP, admin gating."""

import uuid


def _email():
    return f"{uuid.uuid4().hex[:10]}@toolkit.test"


class TestRegisterLogin:
    def test_register_login_whoami(self, client):
        email, password = _email(), "pass-word-123"
        res = client.post("/api/auth/register", json={"email": email, "password": password})
        assert res.status_code == 201
        token = res.get_json()["data"]["token"]
        assert res.get_json()["data"]["user"]["is_admin"] is False  # not the first user

        res = client.post("/api/auth/login", json={"email": email, "password": password})
        assert res.status_code == 200 and res.get_json()["data"]["token"]

        res = client.get("/api/auth/whoami", headers={"Authorization": f"Bearer {token}"})
        assert res.get_json()["data"]["email"] == email

    def test_duplicate_email_conflict(self, client):
        email = _email()
        client.post("/api/auth/register", json={"email": email, "password": "pass-word-123"})
        res = client.post("/api/auth/register", json={"email": email, "password": "pass-word-456"})
        assert res.status_code == 409
        assert res.get_json()["error"]["code"] == "conflict"

    def test_short_password_rejected(self, client):
        res = client.post("/api/auth/register", json={"email": _email(), "password": "short"})
        assert res.status_code == 422

    def test_bad_email_rejected(self, client):
        res = client.post("/api/auth/register", json={"email": "not-an-email", "password": "pass-word-123"})
        assert res.status_code == 422

    def test_login_wrong_password_401_envelope(self, client):
        email = _email()
        client.post("/api/auth/register", json={"email": email, "password": "pass-word-123"})
        res = client.post("/api/auth/login", json={"email": email, "password": "wrong-password"})
        assert res.status_code == 401
        assert res.get_json()["error"]["code"] == "authentication_failed"

    def test_login_unknown_email_same_error(self, client):
        res = client.post("/api/auth/login", json={"email": "ghost@toolkit.test", "password": "whatever-123"})
        assert res.status_code == 401  # indistinguishable from wrong password


class TestTokens:
    def test_garbage_token_401(self, client):
        res = client.get("/api/auth/whoami", headers={"Authorization": "Bearer nonsense"})
        assert res.status_code == 401

    def test_missing_token_401(self, client):
        assert client.get("/api/auth/whoami").status_code == 401

    def test_whoami_requires_bearer_prefix(self, client, user_headers):
        raw = user_headers["Authorization"].removeprefix("Bearer ")
        res = client.get("/api/auth/whoami", headers={"Authorization": raw})
        assert res.status_code == 401


class TestAdminGating:
    def test_anonymous_write_blocked(self, client):
        payload = {"slug": "x", "title": "T", "topic": "aes"}
        assert client.post("/api/lessons/", json=payload).status_code == 401

    def test_non_admin_write_blocked(self, client, user_headers):
        res = client.post(
            "/api/lessons/", json={"slug": "x", "title": "T", "topic": "aes"}, headers=user_headers
        )
        assert res.status_code == 403
        assert res.get_json()["error"]["code"] == "forbidden"

    def test_admin_can_write_and_audit(self, client, admin_headers):
        res = client.get("/api/auth/audit", headers=admin_headers)
        assert res.status_code == 200
        assert res.get_json()["data"]["total"] >= 1  # our own register was audited

    def test_non_admin_audit_blocked(self, client, user_headers):
        assert client.get("/api/auth/audit", headers=user_headers).status_code == 403


class TestTotp:
    def test_setup_enable_login_flow(self, client, app):
        from crypto_toolkit.services import auth_service

        email = _email()
        client.post("/api/auth/register", json={"email": email, "password": "pass-word-123"})
        login = client.post("/api/auth/login", json={"email": email, "password": "pass-word-123"})
        headers = {"Authorization": f'Bearer {login.get_json()["data"]["token"]}'}

        setup = client.post("/api/auth/totp/setup", headers=headers).get_json()["data"]
        secret = setup["secret_base32"]
        assert setup["otpauth_uri"].startswith("otpauth://totp/")

        code = auth_service.totp_code(secret)
        res = client.post(
            "/api/auth/totp/enable", json={"secret_base32": secret, "code": code}, headers=headers
        )
        assert res.get_json()["data"]["totp_enabled"] is True

        # login now demands TOTP
        res = client.post("/api/auth/login", json={"email": email, "password": "pass-word-123"})
        assert res.status_code == 401
        res = client.post(
            "/api/auth/login", json={"email": email, "password": "pass-word-123", "totp_code": "000000"}
        )
        assert res.status_code == 401
        res = client.post(
            "/api/auth/login",
            json={"email": email, "password": "pass-word-123", "totp_code": auth_service.totp_code(secret)},
        )
        assert res.status_code == 200

        # disable: password + valid code
        res = client.post(
            "/api/auth/totp/disable",
            json={"code": auth_service.totp_code(secret), "password": "pass-word-123"},
            headers=headers,
        )
        assert res.get_json()["data"]["totp_enabled"] is False

    def test_totp_verify_window(self, app):
        from crypto_toolkit.services import auth_service

        secret = auth_service.generate_totp_secret()
        assert auth_service.verify_totp(secret, auth_service.totp_code(secret)) is True
        assert auth_service.verify_totp(secret, "12345x") is False
        assert auth_service.verify_totp(secret, "1234567") is False

    def test_totp_code_replay_rejected(self, client, app, monkeypatch):
        """RFC 6238 §5.2: a code that already logged you in must not work twice."""
        from crypto_toolkit.services import auth_service

        email = _email()
        client.post("/api/auth/register", json={"email": email, "password": "pass-word-123"})
        login = client.post("/api/auth/login", json={"email": email, "password": "pass-word-123"})
        headers = {"Authorization": f'Bearer {login.get_json()["data"]["token"]}'}
        secret = client.post("/api/auth/totp/setup", headers=headers).get_json()["data"]["secret_base32"]
        res = client.post(
            "/api/auth/totp/enable",
            json={"secret_base32": secret, "code": auth_service.totp_code(secret)},
            headers=headers,
        )
        assert res.get_json()["data"]["totp_enabled"] is True

        fixed = 1_800_000_000.0
        monkeypatch.setattr(auth_service, "_now", lambda: fixed)
        creds = {"email": email, "password": "pass-word-123"}
        code = auth_service.totp_code(secret)
        assert client.post("/api/auth/login", json={**creds, "totp_code": code}).status_code == 200

        # replaying the same code inside the same timestep -> rejected
        assert client.post("/api/auth/login", json={**creds, "totp_code": code}).status_code == 401

        # the next timestep's fresh code works again
        monkeypatch.setattr(auth_service, "_now", lambda: fixed + 30)
        fresh = auth_service.totp_code(secret)
        assert client.post("/api/auth/login", json={**creds, "totp_code": fresh}).status_code == 200
