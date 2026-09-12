"""Tests for JWT helpers."""

import jwt
import pytest

from app.core import config
from app.core.security import create_access_token, decode_access_token


def test_token_roundtrip():
    token = create_access_token("user-1", {"scope": "admin pharmacy:check"})
    claims = decode_access_token(token)
    assert claims["sub"] == "user-1"
    assert claims["scope"] == "admin pharmacy:check"


def test_decode_invalid_token_raises():
    with pytest.raises(jwt.PyJWTError):
        decode_access_token("not-a-real-token")


def test_expired_token_raises():
    settings = config.get_settings()
    original = settings.access_token_expire_minutes
    settings.access_token_expire_minutes = -1
    try:
        token = create_access_token("user-1")
        with pytest.raises(jwt.ExpiredSignatureError):
            decode_access_token(token)
    finally:
        settings.access_token_expire_minutes = original
