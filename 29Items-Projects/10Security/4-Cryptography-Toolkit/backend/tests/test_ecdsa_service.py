"""ECDSA service unit tests — signing, determinism, and the k-reuse recovery."""

import pytest

from crypto_toolkit.errors import InvalidInputError, VerificationError
from crypto_toolkit.services import ecdsa_service

KP = ecdsa_service.generate_keypair("P-256")


class TestKeygen:
    def test_curves(self):
        assert "BEGIN PUBLIC KEY" in ecdsa_service.generate_keypair("P-384")["public_pem"]

    def test_unsupported_curve(self):
        with pytest.raises(InvalidInputError):
            ecdsa_service.generate_keypair("secp256k1")


class TestSignVerify:
    def test_round_trip_both_modes(self):
        for mode in ("fips-186-3", "deterministic-rfc6979"):
            sig = ecdsa_service.sign(KP["private_pem"], b"hello", mode=mode)
            assert ecdsa_service.verify(KP["public_pem"], b"hello", sig["signature"])["verified"]

    def test_tampered_message_fails(self):
        sig = ecdsa_service.sign(KP["private_pem"], b"hello")
        with pytest.raises(VerificationError):
            ecdsa_service.verify(KP["public_pem"], b"hellO", sig["signature"])

    def test_public_key_cannot_sign(self):
        with pytest.raises(InvalidInputError, match="private"):
            ecdsa_service.sign(KP["public_pem"], b"m")

    def test_bad_mode_rejected(self):
        with pytest.raises(InvalidInputError):
            ecdsa_service.sign(KP["private_pem"], b"m", mode="raw")

    def test_garbage_signature_fails(self):
        import base64

        with pytest.raises((VerificationError, InvalidInputError)):
            ecdsa_service.verify(KP["public_pem"], b"m", base64.b64encode(b"junk").decode())


class TestDeterminism:
    def test_random_vs_deterministic(self):
        result = ecdsa_service.determinism_demo(KP["private_pem"], b"same message")
        assert result["random_signatures_differ"] is True
        assert result["deterministic_signatures_identical"] is True


class TestKReuseRecovery:
    K = 0xC1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1

    def test_explicit_k_signature_verifies_under_standard_path(self):
        """The explicit-k signer produces genuine ECDSA (library-verifiable)."""
        sig = ecdsa_service.sign_with_explicit_k(KP["private_pem"], b"msg", self.K)
        assert ecdsa_service.verify(KP["public_pem"], b"msg", sig["signature"])["verified"]

    def test_recovery_returns_the_actual_private_key(self):
        from Crypto.PublicKey import ECC

        sig1 = ecdsa_service.sign_with_explicit_k(KP["private_pem"], b"first", self.K)
        sig2 = ecdsa_service.sign_with_explicit_k(KP["private_pem"], b"second", self.K)
        result = ecdsa_service.recover_key_from_reused_k(b"first", sig1, b"second", sig2)
        actual_d = int(ECC.import_key(KP["private_pem"]).d)
        assert result["recovered_k"] == self.K
        assert result["recovered_private_key_d"] == actual_d

    def test_different_nonces_cannot_recover(self):
        sig1 = ecdsa_service.sign_with_explicit_k(KP["private_pem"], b"a", self.K)
        sig2 = ecdsa_service.sign_with_explicit_k(KP["private_pem"], b"b", self.K + 1)
        with pytest.raises(InvalidInputError, match="r values differ"):
            ecdsa_service.recover_key_from_reused_k(b"a", sig1, b"b", sig2)

    def test_invalid_k_range_rejected(self):
        with pytest.raises(InvalidInputError):
            ecdsa_service.sign_with_explicit_k(KP["private_pem"], b"m", 0)
