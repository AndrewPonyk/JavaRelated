"""Auth endpoint + JWT/scope tests."""

from __future__ import annotations

import pytest
from app.core.security import (
    authorize,
    create_access_token,
    decode_jwt,
    require_scope,
)
from fastapi import HTTPException


def test_login_success(client):
    resp = client.post("/auth/token", json={"username": "admin", "password": "admin"})
    assert resp.status_code == 200
    body = resp.json()
    assert body["token_type"] == "bearer"
    assert "classify" in body["scopes"]
    assert body["access_token"]


def test_login_bad_credentials(client):
    resp = client.post("/auth/token", json={"username": "admin", "password": "wrong"})
    assert resp.status_code == 401


def test_jwt_roundtrip():
    token = create_access_token("alice", ["classify"], expires_in=60)
    claims = decode_jwt(token)
    assert claims["sub"] == "alice"
    assert "classify" in claims["scopes"]


def test_decode_invalid_token_raises():
    with pytest.raises(HTTPException) as exc:
        decode_jwt("not-a-real-token")
    assert exc.value.status_code == 401


def test_require_scope_allows_matching():
    dep = require_scope("classify")
    claims = {"sub": "u", "scopes": ["classify"]}
    assert dep(claims) == claims


def test_require_scope_admin_override():
    dep = require_scope("taxonomy:write")
    claims = {"sub": "u", "scopes": ["admin"]}
    assert dep(claims) == claims


def test_require_scope_denies_missing():
    dep = require_scope("admin")
    with pytest.raises(HTTPException) as exc:
        dep({"sub": "u", "scopes": ["classify"]})
    assert exc.value.status_code == 403


def test_authorize_with_bearer_token():
    token = create_access_token("svc", ["classify"], expires_in=60)
    dep = authorize("classify")
    # open-dev API-key mode would short-circuit; pass an unknown header path via bearer.
    assert dep(x_api_key=None, authorization=f"Bearer {token}")
