from __future__ import annotations


def test_security_headers_present(client):
    resp = client.get("/healthz")
    assert resp.headers["x-content-type-options"] == "nosniff"
    assert resp.headers["x-frame-options"] == "DENY"
    assert resp.headers["referrer-policy"] == "no-referrer"


def test_assets_pagination_caps_results(client, auth_headers, seeded_assets):
    resp = client.get("/api/v1/assets?limit=2", headers=auth_headers)
    assert resp.status_code == 200
    assert len(resp.json()) <= 2


def test_pagination_rejects_invalid_limit(client, auth_headers):
    assert client.get("/api/v1/assets?limit=0", headers=auth_headers).status_code == 422
    assert client.get("/api/v1/portfolios?skip=-1", headers=auth_headers).status_code == 422


def test_portfolios_pagination(client, auth_headers, portfolio):
    resp = client.get("/api/v1/portfolios?limit=1", headers=auth_headers)
    assert resp.status_code == 200
    assert len(resp.json()) <= 1


def test_jwt_round_trips_with_pyjwt():
    # Auth uses PyJWT now; verify create/decode round-trips and rejects garbage.
    from app.core.security import create_access_token, decode_access_token

    token = create_access_token(subject=123)
    assert decode_access_token(token) == "123"
    assert decode_access_token("not-a-real-token") is None
