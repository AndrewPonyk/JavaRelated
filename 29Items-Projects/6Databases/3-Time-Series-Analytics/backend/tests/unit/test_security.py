"""Credential primitives: password hashing, device keys, JWTs."""

from app.core.security import (
    compose_device_key,
    create_access_token,
    decode_access_token,
    generate_device_secret,
    hash_api_key,
    hash_password,
    split_device_key,
    verify_api_key,
    verify_password,
)


def test_password_roundtrip():
    stored = hash_password("s3cret-passphrase")
    assert stored.startswith("pbkdf2_sha256$")
    assert "s3cret-passphrase" not in stored
    assert verify_password("s3cret-passphrase", stored)


def test_password_rejects_wrong_and_malformed():
    stored = hash_password("right")
    assert not verify_password("wrong", stored)
    assert not verify_password("anything", "not-a-hash")
    assert not verify_password("anything", "md5$1$aa$bb")


def test_password_hashes_are_salted():
    assert hash_password("same") != hash_password("same")


def test_device_key_compose_split_roundtrip():
    secret = generate_device_secret()
    key = compose_device_key("dev-abc123", secret)
    assert split_device_key(key) == ("dev-abc123", secret)


def test_split_device_key_rejects_malformed():
    assert split_device_key("nodothere") is None
    assert split_device_key(".secret-only") is None
    assert split_device_key("device-only.") is None


def test_api_key_hash_verify():
    secret = generate_device_secret()
    stored = hash_api_key(secret)
    assert verify_api_key(secret, stored)
    assert not verify_api_key("wrong-secret", stored)
    assert secret not in stored


def test_jwt_roundtrip_carries_subject_and_roles():
    token = create_access_token("andrii", ["admin", "operator"])
    payload = decode_access_token(token)
    assert payload["sub"] == "andrii"
    assert payload["roles"] == ["admin", "operator"]
    assert payload["exp"] > payload["iat"]
