"""Unit tests for JWT verification and scope enforcement."""
from __future__ import annotations

import time

import pytest

from src.core.errors import AuthenticationError, AuthorizationError
from src.core.security import (
    HS256TokenVerifier,
    Principal,
    encode_hs256,
)

SECRET = "unit-secret"
AUD = "enterprise-ml-platform"


def _token(**overrides) -> str:
    claims = {
        "sub": "u1",
        "email": "u1@example.com",
        "aud": AUD,
        "scope": "experiments:read serving:invoke",
        "exp": int(time.time()) + 60,
    }
    claims.update(overrides)
    return encode_hs256(claims, SECRET)


def test_valid_token_roundtrip() -> None:
    verifier = HS256TokenVerifier(SECRET, AUD)
    principal = verifier.verify(_token())
    assert principal.subject == "u1"
    assert principal.has("experiments:read")
    assert not principal.has("models:promote")


def test_tampered_signature_rejected() -> None:
    verifier = HS256TokenVerifier(SECRET, AUD)
    forged = _token()[:-3] + "xxx"
    with pytest.raises(AuthenticationError):
        verifier.verify(forged)


def test_wrong_secret_rejected() -> None:
    token = _token()
    with pytest.raises(AuthenticationError):
        HS256TokenVerifier("other-secret", AUD).verify(token)


def test_expired_token_rejected() -> None:
    verifier = HS256TokenVerifier(SECRET, AUD)
    with pytest.raises(AuthenticationError):
        verifier.verify(_token(exp=int(time.time()) - 5))


def test_wrong_audience_rejected() -> None:
    verifier = HS256TokenVerifier(SECRET, AUD)
    with pytest.raises(AuthenticationError):
        verifier.verify(_token(aud="some-other-api"))


def test_missing_subject_rejected() -> None:
    verifier = HS256TokenVerifier(SECRET, AUD)
    with pytest.raises(AuthenticationError):
        verifier.verify(_token(sub=""))


def test_malformed_token_rejected() -> None:
    verifier = HS256TokenVerifier(SECRET, AUD)
    with pytest.raises(AuthenticationError):
        verifier.verify("not-a-jwt")


def test_empty_secret_construction_fails() -> None:
    with pytest.raises(ValueError):
        HS256TokenVerifier("", AUD)


def test_admin_scope_grants_everything() -> None:
    p = Principal(subject="a", email="a@x", scopes=frozenset({"admin:*"}))
    p.require("models:promote")  # must not raise


def test_require_missing_scope_raises() -> None:
    p = Principal(subject="a", email="a@x", scopes=frozenset({"experiments:read"}))
    with pytest.raises(AuthorizationError):
        p.require("models:promote")
