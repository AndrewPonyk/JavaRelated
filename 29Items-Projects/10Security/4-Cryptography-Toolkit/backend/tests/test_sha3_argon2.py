"""SHA-3, Argon2, and the stateful SHA-256 midstate (length-extension tool)."""

import hashlib
import os

import pytest

from crypto_toolkit.errors import InvalidInputError, VerificationError
from crypto_toolkit.services import argon2_service, sha3_service, sha256_midstate


class TestSha3:
    def test_fips_202_vectors(self):
        # FIPS 202 Appendix A: first hex bytes of SHA3("abc")
        for algo, prefix in {
            "sha3-256": "3a985da7",
            "sha3-384": "ec014982",
            "sha3-512": "b751850b",
        }.items():
            result = sha3_service.digest(b"abc", algo)
            assert result["digest_hex"].startswith(prefix), algo

    def test_digest_lengths(self):
        assert len(sha3_service.digest(b"x", "sha3-256")["digest_hex"]) == 64
        assert sha3_service.digest(b"x", "sha3-384")["digest_bits"] == 384

    def test_unknown_algorithm(self):
        with pytest.raises(InvalidInputError):
            sha3_service.digest(b"x", "sha3-224")


class TestArgon2:
    def test_hash_verify_round_trip(self):
        result = argon2_service.hash_password("correct horse battery", "interactive")
        assert result["preset"] == "interactive" and result["hash"].startswith("$argon2id$")
        assert argon2_service.verify_password(result["hash"], "correct horse battery")["verified"]

    def test_wrong_password_raises(self):
        h = argon2_service.hash_password("right", "interactive")["hash"]
        with pytest.raises(VerificationError):
            argon2_service.verify_password(h, "wrong")

    def test_malformed_hash_raises_invalid_input(self):
        with pytest.raises(InvalidInputError):
            argon2_service.verify_password("not-a-hash", "x")

    def test_unknown_preset(self):
        with pytest.raises(InvalidInputError):
            argon2_service.hash_password("x", "ultra")

    def test_empty_password_rejected(self):
        with pytest.raises(InvalidInputError):
            argon2_service.hash_password("", "interactive")

    def test_presets_produce_different_hashes(self):
        a = argon2_service.hash_password("pw", "interactive")["hash"]
        b = argon2_service.hash_password("pw", "moderate")["hash"]
        assert a != b  # params differ -> encoded hash differs


class TestSha256Midstate:
    def test_parity_with_hashlib(self):
        for msg in (b"", b"abc", b"a" * 55, b"a" * 56, b"a" * 64, os.urandom(200)):
            assert sha256_midstate.sha256(msg) == hashlib.sha256(msg).digest()

    def test_padding_shape(self):
        pad = sha256_midstate.md_padding(3)
        assert pad[0] == 0x80
        assert (3 + len(pad)) % 64 == 0  # message + padding is block-aligned
        assert pad[-8:] == (24).to_bytes(8, "big")  # 3 bytes = 24 bits

    def test_state_resumption_forge(self):
        """digest_from_state(digest(m), suffix) == sha256(m + pad(m) + suffix)."""
        m, suffix = b"secret-prefixed-message", b"&admin=true"
        base = hashlib.sha256(m).digest()
        pad = sha256_midstate.md_padding(len(m))
        forged = sha256_midstate.digest_from_state(base, suffix, len(m) + len(pad))
        expected = hashlib.sha256(m + pad + suffix).digest()
        assert forged == expected

    def test_state_length_check(self):
        with pytest.raises(ValueError):
            sha256_midstate.digest_from_state(b"short", b"x", 0)
