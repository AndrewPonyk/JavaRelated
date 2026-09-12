"""Production-hardening behaviors: rate limiting, compression, security
headers, request-ID sanitization, placeholder-secret refusal, token edge cases.
"""

import pytest
from fastapi.testclient import TestClient


def _fresh_client(monkeypatch, **env: str) -> TestClient:
    for key, value in env.items():
        monkeypatch.setenv(key, value)
    from tests.conftest import _clear_runtime_caches

    _clear_runtime_caches()
    from app.main import create_app

    return TestClient(create_app(), raise_server_exceptions=False)


class TestRateLimiting:
    def test_post_endpoints_return_429_past_the_limit(self, app_env, monkeypatch):
        client = _fresh_client(monkeypatch, RATE_LIMIT_PER_MINUTE="2")
        payload = {"expression": "x - 1 = 0"}
        first = client.post("/api/v1/symbolic/render", json={"expression": "x"})
        second = client.post("/api/v1/symbolic/render", json={"expression": "x"})
        third = client.post("/api/v1/symbolic/solve", json=payload)
        assert first.status_code == 200
        assert second.status_code == 200
        assert third.status_code == 429
        assert third.headers["content-type"].startswith("application/problem+json")
        assert "Retry-After" in third.headers
        assert third.json()["request_id"]  # 429s are still correlated

    def test_get_endpoints_are_not_limited(self, app_env, monkeypatch):
        client = _fresh_client(monkeypatch, RATE_LIMIT_PER_MINUTE="1")
        for _ in range(5):
            assert client.get("/healthz").status_code == 200

    def test_zero_disables_limiting(self, app_env, monkeypatch):
        client = _fresh_client(monkeypatch, RATE_LIMIT_PER_MINUTE="0")
        for _ in range(5):
            response = client.post("/api/v1/symbolic/render", json={"expression": "x"})
            assert response.status_code == 200


class TestTransportHardening:
    def test_large_responses_are_gzipped(self, client):
        # The OpenAPI schema is tens of KiB — comfortably past minimum_size.
        response = client.get("/api/v1/openapi.json", headers={"Accept-Encoding": "gzip"})
        assert response.status_code == 200
        assert response.headers.get("content-encoding") == "gzip"

    def test_small_responses_stay_uncompressed(self, client):
        response = client.get("/healthz", headers={"Accept-Encoding": "gzip"})
        assert response.status_code == 200
        assert response.headers.get("content-encoding") is None

    def test_security_headers_present_on_every_response(self, client):
        response = client.get("/healthz")
        assert response.headers["X-Content-Type-Options"] == "nosniff"
        assert response.headers["X-Frame-Options"] == "DENY"
        assert response.headers["Referrer-Policy"] == "strict-origin-when-cross-origin"

    def test_hostile_request_id_is_sanitized(self, client):
        response = client.get("/healthz", headers={"X-Request-ID": "evil id<script>" + "x" * 200})
        echoed = response.headers["X-Request-ID"]
        assert "<" not in echoed and " " not in echoed
        assert len(echoed) <= 64

    def test_clean_request_id_still_round_trips(self, client):
        response = client.get("/healthz", headers={"X-Request-ID": "trace-1.2_A"})
        assert response.headers["X-Request-ID"] == "trace-1.2_A"


class TestBootGuards:
    def test_placeholder_secret_refused_in_prod(self, app_env, monkeypatch):
        monkeypatch.setenv("ENVIRONMENT", "prod")
        monkeypatch.setenv("JWT_SECRET_KEY", "change-me-in-anything-but-local-0123456789")
        from tests.conftest import _clear_runtime_caches

        _clear_runtime_caches()
        from app.main import create_app

        with pytest.raises(RuntimeError, match="JWT_SECRET_KEY"):
            create_app()

    def test_real_secret_accepted_in_prod(self, app_env, monkeypatch):
        client = _fresh_client(
            monkeypatch,
            ENVIRONMENT="prod",
            JWT_SECRET_KEY="a-genuinely-configured-secret-0123456789abcdef",
        )
        assert client.get("/healthz").status_code == 200


class TestTokenEdgeCases:
    def test_malformed_bearer_token_is_401_not_500(self, client):
        response = client.get("/api/v1/computations", headers={"Authorization": "Bearer not.a.jwt"})
        assert response.status_code == 401

    def test_token_with_non_uuid_subject_is_401(self, client, app_env):
        from app.core.config import get_settings
        from app.core.security import _encode

        forged = _encode(
            subject="not-a-uuid",
            token_type="access",
            ttl_seconds=60,
            settings=get_settings(),
            extra={"role": "student"},
        ).token
        response = client.get("/api/v1/computations", headers={"Authorization": f"Bearer {forged}"})
        assert response.status_code == 401
