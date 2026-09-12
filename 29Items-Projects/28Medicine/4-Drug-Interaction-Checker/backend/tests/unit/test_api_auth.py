"""API tests for the dev token endpoint."""

from app.core import config


def test_issue_token_with_scopes(client):
    resp = client.post("/api/v1/auth/token", json={"subject": "u", "scopes": ["admin"]})
    assert resp.status_code == 200
    body = resp.json()
    assert body["token_type"] == "bearer"
    assert "admin" in body["scope"]
    assert body["access_token"]


def test_issue_token_defaults(client):
    resp = client.post("/api/v1/auth/token", json={})
    assert resp.status_code == 200
    assert "pharmacy:check" in resp.json()["scope"]


def test_token_disabled_in_production(client):
    settings = config.get_settings()
    original = settings.environment
    settings.environment = "production"
    try:
        assert client.post("/api/v1/auth/token", json={}).status_code == 404
    finally:
        settings.environment = original
