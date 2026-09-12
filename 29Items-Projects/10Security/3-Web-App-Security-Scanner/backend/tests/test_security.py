"""Unit tests: password hashing, JWT round-trips, API key generation."""

from __future__ import annotations

import time

import pytest
from app.core import security
from app.core.security import (
    TokenError,
    api_key_prefix,
    create_access_token,
    create_refresh_token,
    decode_token,
    generate_api_key,
    hash_api_key,
    hash_password,
    role_rank,
    verify_password,
)


class TestPasswords:
    def test_round_trip(self):
        hashed = hash_password("correct horse battery staple")
        assert hashed.startswith("pbkdf2_sha256$")
        assert "correct" not in hashed
        assert verify_password("correct horse battery staple", hashed)

    def test_wrong_password_rejected(self):
        hashed = hash_password("right-password-1")
        assert not verify_password("wrong-password-1", hashed)

    def test_salt_makes_hashes_unique(self):
        assert hash_password("same") != hash_password("same")

    def test_malformed_hash_rejected(self):
        assert not verify_password("x", "not-a-hash")
        assert not verify_password("x", "argon2id$1$2$3")


class TestJwt:
    def test_round_trip(self):
        token = create_access_token(42, "scanner")
        claims = decode_token(token)
        assert claims["sub"] == "42"
        assert claims["role"] == "scanner"
        assert claims["type"] == "access"

    def test_refresh_type_distinguished(self):
        refresh = create_refresh_token(42, "viewer")
        with pytest.raises(TokenError, match="expected access"):
            decode_token(refresh)
        assert decode_token(refresh, expected_type="refresh")["sub"] == "42"

    def test_expired_token_rejected(self, monkeypatch):
        token = create_access_token(1, "viewer")
        real_time = time.time
        monkeypatch.setattr(time, "time", lambda: real_time() + 999_999)
        with pytest.raises(TokenError, match="expired"):
            decode_token(token)

    def test_tampered_payload_rejected(self):
        token = create_access_token(1, "viewer")
        header, body, sig = token.split(".")
        # forge a body claiming the admin role, keep the old signature
        import base64
        import json

        forged = (
            base64.urlsafe_b64encode(
                json.dumps({"sub": "1", "role": "admin", "type": "access", "exp": None}).encode()
            )
            .rstrip(b"=")
            .decode()
        )
        with pytest.raises(TokenError):
            decode_token(f"{header}.{forged}.{sig}")

    def test_garbage_token_rejected(self):
        for bad in ("", "a.b", "a.b.c.d", "!!!.!!.!!", "x.y.not-base64!!"):
            with pytest.raises(TokenError):
                decode_token(bad)

    def test_wrong_signature_key_rejected(self, monkeypatch):
        token = create_access_token(1, "viewer")
        monkeypatch.setattr(security.settings, "SECRET_KEY", "other-key")
        with pytest.raises(TokenError, match="signature"):
            decode_token(token)


class TestApiKeys:
    def test_generation_shape(self):
        secret, prefix, hashed = generate_api_key()
        assert secret.startswith("wss_")
        assert prefix == api_key_prefix(secret)
        assert prefix.startswith("wss_") and len(prefix) == 10
        assert hashed == hash_api_key(secret)
        assert "wss_" not in hashed

    def test_prefix_indexes_without_leaking_secret(self):
        secret, prefix, _ = generate_api_key()
        assert secret not in prefix


class TestRoleRank:
    def test_hierarchy(self):
        assert role_rank("viewer") < role_rank("scanner") < role_rank("admin")
        assert role_rank("bogus") == -1
