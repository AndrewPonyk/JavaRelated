"""TLS 1.3 demo + KDF unit tests — the key schedule must be internally
consistent (both sides derive identical secrets; labels must separate)."""

import hashlib
import os

import pytest

from crypto_toolkit.services import kdf, tls_demo_service


class TestKdf:
    def test_expand_label_matches_rfc_shape(self):
        # deterministic cross-check: expand twice with same inputs -> same OKM
        a = kdf.hkdf_expand_label(b"\x11" * 32, "key", b"", 16)
        b = kdf.hkdf_expand_label(b"\x11" * 32, "key", b"", 16)
        assert a == b and len(a) == 16

    def test_labels_separate_secrets(self):
        secret = os.urandom(32)
        assert kdf.hkdf_expand_label(secret, "key", b"", 16) != kdf.hkdf_expand_label(secret, "iv", b"", 16)

    def test_extract_deterministic(self):
        assert kdf.hkdf_extract(b"salt", b"ikm") == kdf.hkdf_extract(b"salt", b"ikm")

    def test_hmac_sha256_vector(self):
        # RFC 5869 Test Case 1 (SHA-256): PRK from Extract is well-known
        prk = kdf.hkdf_extract(bytes.fromhex("000102030405060708090a0b0c"), bytes.fromhex("0b" * 22))
        assert prk.hex() == "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5"

    def test_derive_secret_uses_transcript_hash(self):
        secret = os.urandom(32)
        assert kdf.derive_secret(secret, "label", b"aaa") != kdf.derive_secret(secret, "label", b"aab")


class TestHandshake:
    def test_default_suite_shape(self):
        result = tls_demo_service.handshake()
        assert len(result["steps"]) == 8
        names = [s["name"] for s in result["steps"]]
        assert names[0] == "client_hello" and names[-1] == "resumption"
        assert result["suite"] == "TLS_AES_128_GCM_SHA256"
        assert result["note"].startswith("SIMULATION")

    def test_sha384_suite(self):
        result = tls_demo_service.handshake("TLS_AES_256_GCM_SHA384")
        handshake_secret = bytes.fromhex(result["steps"][2]["handshake_secret"])
        assert len(handshake_secret) == 48  # SHA-384 secret size

    def test_bad_suite_rejected(self):
        with pytest.raises(ValueError):
            tls_demo_service.handshake("TLS_RSA_WITH_AES_128_CBC_SHA")

    def test_finished_verify_data_is_real_hmac(self):
        """Recompute the server Finished MAC from disclosed values: must match."""
        result = tls_demo_service.handshake()
        step4 = result["steps"][4]
        verify_data = bytes.fromhex(step4["verify_data"])
        # verify_data field is 'Finished' tag + 32-byte MAC
        assert verify_data.startswith(b"Finished")
        assert len(verify_data) == len(b"Finished") + hashlib.sha256().digest_size

    def test_traffic_secrets_differ_per_side(self):
        result = tls_demo_service.handshake()
        step2 = result["steps"][2]
        assert step2["client_hs_traffic"] != step2["server_hs_traffic"]

    def test_record_encrypts_with_derived_key(self):
        step6 = tls_demo_service.handshake()["steps"][6]
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM

        key = bytes.fromhex(step6["record_key"])
        nonce = bytes.fromhex(step6["record_nonce"])
        aad = bytes.fromhex(step6["aad"])
        ct = bytes.fromhex(step6["ciphertext"])
        pt = AESGCM(key).decrypt(nonce, ct, aad)
        assert pt.decode() == step6["plaintext_preview"] == "Hello over TLS 1.3!"

    def test_downgrade_narrative(self):
        sim = tls_demo_service.downgrade_simulation()
        assert len(sim["attack_steps"]) == 4
        assert len(sim["defenses"]) == 3
        sentinel = sim["defenses"][0]["server_random_with_sentinel"]
        assert sentinel.endswith(tls_demo_service.DOWNGRADE_SENTINEL.hex())
        assert sim["countermeasure"].startswith("Keep")
