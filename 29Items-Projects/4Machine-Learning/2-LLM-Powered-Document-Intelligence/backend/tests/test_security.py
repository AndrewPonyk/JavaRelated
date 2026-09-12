"""Unit tests for JWT auth helpers."""

from __future__ import annotations

import pytest

from app.core import security
from app.core.config import settings
from app.core.security import AuthError, make_dev_token, verify_token


def test_make_and_verify_dev_token_roundtrip() -> None:
    token = make_dev_token("tenant-x", subject="alice", scopes=("query:run",))
    principal = verify_token(token)
    assert principal.tenant_id == "tenant-x"
    assert principal.subject == "alice"
    assert principal.has_scope("query:run")
    assert not principal.has_scope("documents:write")


def test_token_without_tenant_is_rejected() -> None:
    import jwt

    bad = jwt.encode({"sub": "no-tenant"}, settings.jwt_dev_secret, algorithm="HS256")
    with pytest.raises(AuthError):
        verify_token(bad)


def test_empty_token_rejected() -> None:
    with pytest.raises(AuthError):
        verify_token("")


def test_non_jwt_bearer_maps_to_dev_principal_in_dev() -> None:
    # The frontend's literal "dev-token" should resolve to the default dev principal.
    principal = verify_token("dev-token")
    assert principal.tenant_id == "dev-tenant"


def test_production_fails_closed_without_jwks(monkeypatch) -> None:
    monkeypatch.setattr(settings, "app_env", "production")
    monkeypatch.setattr(settings, "jwt_jwks_url", "")
    with pytest.raises(AuthError):
        verify_token(make_dev_token("tenant-x"))
    # sanity: the helper reads the live setting
    assert security.settings.is_production
