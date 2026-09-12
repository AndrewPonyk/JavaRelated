"""Token and password-hashing primitives."""

import pytest

from app.core.config import Settings
from app.core.security import (
    TokenError,
    create_access_token,
    create_refresh_token,
    decode_token,
    hash_password,
    verify_password,
)


@pytest.fixture()
def settings() -> Settings:
    return Settings(jwt_secret_key="unit-test-secret-0123456789abcdefghij", _env_file=None)


class TestPasswords:
    def test_hash_verify_roundtrip(self):
        stored = hash_password("s3cure-enough")
        assert stored.startswith("scrypt$")
        assert verify_password("s3cure-enough", stored)
        assert not verify_password("wrong", stored)

    def test_salts_differ_per_hash(self):
        assert hash_password("same") != hash_password("same")

    def test_garbage_stored_hash_never_raises(self):
        assert not verify_password("x", "not-a-hash")
        assert not verify_password("x", "scrypt$bad$fields")


class TestTokens:
    def test_access_token_roundtrip(self, settings):
        token = create_access_token(subject="user-1", role="instructor", settings=settings)
        payload = decode_token(token, settings, expected_type="access")
        assert payload["sub"] == "user-1"
        assert payload["role"] == "instructor"

    def test_refresh_token_carries_jti_and_expiry(self, settings):
        issued = create_refresh_token(subject="user-1", settings=settings)
        payload = decode_token(issued.token, settings, expected_type="refresh")
        assert payload["jti"] == issued.jti
        assert issued.expires_at.tzinfo is not None

    def test_type_confusion_rejected(self, settings):
        access = create_access_token(subject="u", role="student", settings=settings)
        with pytest.raises(TokenError, match="refresh"):
            decode_token(access, settings, expected_type="refresh")

    def test_tampered_token_rejected(self, settings):
        token = create_access_token(subject="u", role="student", settings=settings)
        with pytest.raises(TokenError):
            decode_token(token[:-4] + "AAAA", settings, expected_type="access")

    def test_wrong_secret_rejected(self, settings):
        other = Settings(jwt_secret_key="different-secret-0123456789abcdefghij", _env_file=None)
        token = create_access_token(subject="u", role="student", settings=settings)
        with pytest.raises(TokenError):
            decode_token(token, other, expected_type="access")

    def test_expired_token_rejected(self, settings):
        expired = Settings(
            jwt_secret_key="unit-test-secret-0123456789abcdefghij",
            access_token_ttl_seconds=-10,
            _env_file=None,
        )
        token = create_access_token(subject="u", role="student", settings=expired)
        with pytest.raises(TokenError):
            decode_token(token, settings, expected_type="access")
