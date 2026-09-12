import time

import pytest
from conftest import build_client

from app.config import get_settings


def test_api_key_mode_rejects_and_accepts(monkeypatch, fake_redis):
    monkeypatch.setenv("AUTH_MODE", "api_key")
    monkeypatch.setenv("API_KEY", "s3kret")
    get_settings.cache_clear()
    client = build_client(fake_redis)

    assert client.get("/api/v1/pipelines").status_code == 401
    assert client.get("/api/v1/pipelines", headers={"X-API-Key": "wrong"}).status_code == 401
    assert client.get("/api/v1/pipelines", headers={"X-API-Key": "s3kret"}).status_code == 200
    # query-param variant (the WebSocket path in browsers)
    assert client.get("/api/v1/pipelines?api_key=s3kret").status_code == 200
    # ops probe stays open for load balancer health checks
    assert client.get("/healthz").status_code == 200


def test_api_key_mode_with_empty_configured_key_fails_closed(monkeypatch, fake_redis):
    monkeypatch.setenv("AUTH_MODE", "api_key")
    monkeypatch.setenv("API_KEY", "")
    get_settings.cache_clear()
    client = build_client(fake_redis)

    assert client.get("/api/v1/pipelines", headers={"X-API-Key": ""}).status_code == 401


def test_jwt_mode_hs256(monkeypatch, fake_redis):
    pyjwt = pytest.importorskip("jwt", reason="PyJWT not installed on this host")
    monkeypatch.setenv("AUTH_MODE", "jwt")
    monkeypatch.setenv("JWT_SECRET", "unit-test-secret-of-32-bytes-min!")
    monkeypatch.setenv("JWT_AUDIENCE", "etl-ui")
    get_settings.cache_clear()
    client = build_client(fake_redis)

    def token(secret="unit-test-secret-of-32-bytes-min!", exp_offset=300, aud="etl-ui"):
        return pyjwt.encode(
            {"sub": "andrii", "aud": aud, "exp": int(time.time()) + exp_offset},
            secret,
            algorithm="HS256",
        )

    assert client.get("/api/v1/pipelines").status_code == 401
    ok = client.get("/api/v1/pipelines", headers={"Authorization": f"Bearer {token()}"})
    assert ok.status_code == 200
    bad_sig = client.get(
        "/api/v1/pipelines",
        headers={"Authorization": f"Bearer {token(secret='forged-secret-of-32-bytes-min!!!')}"},
    )
    assert bad_sig.status_code == 401
    expired = client.get(
        "/api/v1/pipelines", headers={"Authorization": f"Bearer {token(exp_offset=-60)}"}
    )
    assert expired.status_code == 401
    wrong_aud = client.get(
        "/api/v1/pipelines", headers={"Authorization": f"Bearer {token(aud='other-app')}"}
    )
    assert wrong_aud.status_code == 401
