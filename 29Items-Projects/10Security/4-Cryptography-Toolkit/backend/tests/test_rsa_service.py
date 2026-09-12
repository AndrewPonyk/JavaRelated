"""RSA service unit tests — OAEP/PSS round-trips, validation, malleability."""

import base64
import os

import pytest

from crypto_toolkit.errors import InvalidInputError, VerificationError
from crypto_toolkit.services import rsa_service

KP2048 = rsa_service.generate_keypair(2048)


class TestKeygen:
    def test_default_keygen(self):
        kp = rsa_service.generate_keypair()
        assert kp["bits"] == 2048
        assert "PUBLIC KEY" in kp["public_pem"]
        assert "PRIVATE KEY" in kp["private_pem"]

    @pytest.mark.parametrize("bits", [2048, 3072, 4096])
    def test_keygen_sizes(self, bits):
        assert rsa_service.generate_keypair(bits)["bits"] == bits

    def test_weak_key_rejected(self):
        with pytest.raises(InvalidInputError):
            rsa_service.generate_keypair(1024)

    def test_odd_bits_rejected(self):
        with pytest.raises(InvalidInputError):
            rsa_service.generate_keypair(2050)


class TestOaep:
    def test_round_trip(self):
        enc = rsa_service.encrypt(KP2048["public_pem"], b"short message")
        dec = rsa_service.decrypt(KP2048["private_pem"], enc["ciphertext"])
        assert dec["plaintext"] == "short message"

    def test_too_long_message_rejected(self):
        with pytest.raises(InvalidInputError, match="hybrid"):
            rsa_service.encrypt(KP2048["public_pem"], b"x" * 2000)

    def test_private_pem_rejected_for_encrypt(self):
        with pytest.raises(InvalidInputError, match="PUBLIC"):
            rsa_service.encrypt(KP2048["private_pem"], b"m")

    def test_garbled_ciphertext_raises_verification(self):
        with pytest.raises(VerificationError):
            rsa_service.decrypt(KP2048["private_pem"], base64.b64encode(os.urandom(256)).decode())

    def test_bad_base64_rejected(self):
        with pytest.raises(InvalidInputError):
            rsa_service.decrypt(KP2048["private_pem"], "!!!not-base64!!!")

    def test_invalid_pem_rejected(self):
        with pytest.raises(InvalidInputError):
            rsa_service.encrypt("not a pem at all", b"m")


class TestPss:
    def test_round_trip(self):
        sig = rsa_service.sign(KP2048["private_pem"], b"document bytes")
        assert rsa_service.verify(KP2048["public_pem"], b"document bytes", sig["signature"])["verified"]

    def test_tampered_message_fails(self):
        sig = rsa_service.sign(KP2048["private_pem"], b"document bytes")
        with pytest.raises(VerificationError):
            rsa_service.verify(KP2048["public_pem"], b"document BYTES", sig["signature"])

    def test_tampered_signature_fails(self):
        raw = bytearray(base64.b64decode(rsa_service.sign(KP2048["private_pem"], b"m")["signature"]))
        raw[0] ^= 0xFF
        with pytest.raises(VerificationError):
            rsa_service.verify(KP2048["public_pem"], b"m", base64.b64encode(bytes(raw)).decode())

    def test_public_key_cannot_sign(self):
        with pytest.raises(InvalidInputError, match="private"):
            rsa_service.sign(KP2048["public_pem"], b"m")


class TestMalleabilityDemo:
    def test_deterministic_and_doubling(self):
        result = rsa_service.textbook_malleability(KP2048["public_pem"], KP2048["private_pem"], b"42")
        assert result["deterministic"] is True
        assert result["attacker_needs_only_public_key"] is True
        assert result["decrypted_int"] == int.from_bytes(b"42", "big") * 2
        assert result["countermeasure"].startswith("RSA-OAEP")

    def test_message_too_large_rejected(self):
        with pytest.raises(InvalidInputError):
            rsa_service.textbook_malleability(KP2048["public_pem"], KP2048["private_pem"], b"x" * 4096)
