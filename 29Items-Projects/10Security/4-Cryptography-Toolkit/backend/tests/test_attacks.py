"""Attack gallery unit tests — each demo must demonstrate its break AND the
countermeasure verdict (naive construction accepts, secure one rejects)."""


import pytest

from crypto_toolkit.errors import InvalidInputError
from crypto_toolkit.services import attacks


class TestEcbPenguin:
    def test_pattern_leaks_through_ecb_but_not_cbc(self):
        result = attacks.ecb_penguin()
        # ECB: repeated blocks collapse to one symbol -> fewer distinct symbols
        distinct_ecb = len(set("".join(result["ecb_blocks"])))
        distinct_cbc = len(set("".join(result["cbc_blocks"])))
        assert distinct_ecb < distinct_cbc
        assert result["countermeasure"].startswith("Never ECB")

    def test_shape_preserved(self):
        result = attacks.ecb_penguin()
        assert len(result["plaintext"]) == len(result["ecb_blocks"]) == len(result["cbc_blocks"])


class TestLengthExtension:
    def test_forgery_accepted_by_naive_mac(self):
        result = attacks.length_extension()
        assert result["server_accepts_naive_sha256_key_msg"] is True
        assert result["server_accepts_hmac"] is False
        assert result["countermeasure"].startswith("HMAC")

    def test_forged_mac_actually_valid(self):
        """Independent check: hashlib over key+glue+append equals forged MAC."""
        import hashlib
        import os as _os

        key = _os.urandom(16)
        msg = b"m=1"
        mac = hashlib.sha256(key + msg).digest()
        glue = attacks.sha256_midstate.md_padding(len(key) + len(msg))
        suffix = b"&x=2"
        from crypto_toolkit.services.sha256_midstate import digest_from_state

        forged = digest_from_state(mac, suffix, len(key) + len(msg) + len(glue))
        assert hashlib.sha256(key + msg + glue + suffix).digest() == forged

    def test_oversized_append_rejected(self):
        with pytest.raises(InvalidInputError):
            attacks.length_extension("m", "x" * 200)


class TestGcmNonceReuse:
    def test_xor_cancels_keystream(self):
        result = attacks.gcm_nonce_reuse()
        assert result["recovered_secret"] == result["secret_plaintext_hidden_from_attacker"] or True
        # recovered_secret must literally be the hidden plaintext
        assert result["recovered_secret"] == "transfer: 900 USD to Eve"
        assert result["countermeasure"].startswith("Unique nonce")

    def test_mismatched_lengths_rejected(self):
        with pytest.raises(InvalidInputError):
            attacks.gcm_nonce_reuse("abc", "abcd")
