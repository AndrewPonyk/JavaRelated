"""Unit tests for the AES service — the round-trip + tamper pattern
every cipher service should follow (TECH-NOTES §3.2)."""

import base64
import os

import pytest

from crypto_toolkit.errors import (
    InvalidInputError,
    UnsupportedOperationError,
    VerificationError,
)
from crypto_toolkit.services import aes_service

KEY32 = os.urandom(32)


class TestGcmRoundTrip:
    def test_encrypt_then_decrypt_returns_plaintext(self):
        payload = aes_service.encrypt(b"attack at dawn", KEY32)
        result = aes_service.decrypt(payload, KEY32)
        assert result["plaintext"] == "attack at dawn"

    def test_identical_plaintexts_get_fresh_ivs(self):
        # Nonce reuse is catastrophic (TECH-NOTES §3.6 #3)
        p1 = aes_service.encrypt(b"same", KEY32)
        p2 = aes_service.encrypt(b"same", KEY32)
        assert p1["iv"] != p2["iv"] and p1["ciphertext"] != p2["ciphertext"]

    def test_tampered_ciphertext_fails_tag(self):
        payload = aes_service.encrypt(b"attack at dawn", KEY32)
        raw = bytearray(base64.b64decode(payload["ciphertext"]))
        raw[0] ^= 0x01
        payload["ciphertext"] = base64.b64encode(bytes(raw)).decode()
        with pytest.raises(VerificationError):
            aes_service.decrypt(payload, KEY32)

    def test_wrong_key_fails_tag(self):
        payload = aes_service.encrypt(b"secret", KEY32)
        with pytest.raises(VerificationError):
            aes_service.decrypt(payload, os.urandom(32))


class TestValidation:
    def test_ecb_rejected_outside_demo(self):
        with pytest.raises(UnsupportedOperationError):
            aes_service.encrypt(b"x", KEY32, mode="ecb")

    def test_ecb_allowed_in_demo_with_warning(self):
        result = aes_service.encrypt(b"x" * 64, KEY32, mode="ecb", demo=True)
        assert "warning" in result

    def test_bad_key_length_rejected(self):
        with pytest.raises(InvalidInputError):
            aes_service.encrypt(b"x", b"short", mode="gcm")

    def test_empty_plaintext_rejected(self):
        with pytest.raises(InvalidInputError):
            aes_service.encrypt(b"", KEY32, mode="gcm")

    def test_cbc_and_ecb_round_trip(self):
        for mode in ("cbc", "ecb"):
            enc = aes_service.encrypt(b"round trip body", KEY32, mode=mode, demo=True)
            dec = aes_service.decrypt(enc, KEY32)
            assert dec["plaintext"] == "round trip body"
            assert dec["mode"] == mode

    def test_cbc_corrupted_padding_raises_verification(self):
        import base64 as _b64

        enc = aes_service.encrypt(b"pad me please", KEY32, mode="cbc", demo=True)
        raw = bytearray(_b64.b64decode(enc["ciphertext"]))
        raw[-1] ^= 0x05  # break PKCS#7 padding
        enc["ciphertext"] = _b64.b64encode(bytes(raw)).decode()
        with pytest.raises(VerificationError):
            aes_service.decrypt(enc, KEY32)

    def test_cbc_wrong_iv_length_rejected(self):
        import base64 as _b64

        enc = aes_service.encrypt(b"x" * 16, KEY32, mode="cbc", demo=True)
        enc["iv"] = _b64.b64encode(b"short").decode()
        with pytest.raises(InvalidInputError):
            aes_service.decrypt(enc, KEY32)

    def test_decrypt_bad_base64_rejected(self):
        with pytest.raises(InvalidInputError):
            aes_service.decrypt({"ciphertext": "%%%", "mode": "gcm"}, KEY32)

    def test_decrypt_unknown_mode_rejected(self):
        with pytest.raises(UnsupportedOperationError):
            aes_service.decrypt({"ciphertext": "AAAA", "mode": "ctr"}, KEY32)
