"""AES service — authenticated encryption by default.

Secure path: AES-256-GCM with a fresh 96-bit IV per call.
Demo path: ECB/CBC illustration of *why* authenticated modes matter
(only reachable via demo=True from the attack gallery).
"""

from __future__ import annotations

import base64
import os

from Crypto.Cipher import AES

from crypto_toolkit.errors import InvalidInputError, UnsupportedOperationError, VerificationError

SECURE_MODES = frozenset({"gcm"})
DEMO_MODES = frozenset({"ecb", "cbc"})  # attack-gallery only, never default
IV_BYTES = 12  # GCM standard nonce size
TAG_BYTES = 16


def encrypt(plaintext: bytes, key: bytes, mode: str = "gcm", *, demo: bool = False) -> dict:
    """Encrypt bytes. Returns base64 fields ready for JSON transport."""
    if mode not in SECURE_MODES and not (demo and mode in DEMO_MODES):
        raise UnsupportedOperationError(
            f"Mode {mode!r} is not allowed outside the attack gallery (demo=True)."
        )
    if len(key) not in (16, 24, 32):
        raise InvalidInputError("AES key must be 16, 24, or 32 bytes (128/192/256-bit).")
    if not plaintext:
        raise InvalidInputError("Plaintext must not be empty.")

    if mode == "gcm":
        iv = os.urandom(IV_BYTES)
        cipher = AES.new(key, AES.MODE_GCM, nonce=iv)
        ciphertext, tag = cipher.encrypt_and_digest(plaintext)
        return {
            "mode": "gcm",
            "ciphertext": base64.b64encode(ciphertext).decode(),
            "iv": base64.b64encode(iv).decode(),
            "tag": base64.b64encode(tag).decode(),
        }

    if mode == "cbc":
        iv = os.urandom(16)
        padded = _pkcs7_pad(plaintext)
        cipher = AES.new(key, AES.MODE_CBC, iv=iv)
        return {
            "mode": "cbc",
            "ciphertext": base64.b64encode(cipher.encrypt(padded)).decode(),
            "iv": base64.b64encode(iv).decode(),
            "warning": "CBC provides no authentication — pair with HMAC or use GCM.",
        }

    # mode == "ecb" — deterministic, pattern-leaking; demo only.
    cipher = AES.new(key, AES.MODE_ECB)
    return {
        "mode": "ecb",
        "ciphertext": base64.b64encode(cipher.encrypt(_pkcs7_pad(plaintext))).decode(),
        "warning": "ECB leaks plaintext patterns — never use outside this demo.",
    }


def decrypt(payload: dict, key: bytes) -> dict:
    """Decrypt a payload produced by encrypt(). Verifies the GCM tag."""
    mode = payload.get("mode", "gcm")
    try:
        ciphertext = base64.b64decode(payload["ciphertext"])
    except (KeyError, ValueError) as exc:
        raise InvalidInputError(f"ciphertext missing or not valid base64: {exc}") from exc

    if mode == "gcm":
        try:
            iv = base64.b64decode(payload["iv"])
            tag = base64.b64decode(payload["tag"])
        except (KeyError, ValueError) as exc:
            raise InvalidInputError(f"iv/tag missing or not valid base64: {exc}") from exc
        cipher = AES.new(key, AES.MODE_GCM, nonce=iv)
        try:
            plaintext = cipher.decrypt_and_verify(ciphertext, tag)
        except ValueError as exc:
            raise VerificationError(f"GCM tag verification failed: {exc}") from exc
        return {"plaintext": plaintext.decode("utf-8", errors="replace"), "mode": mode}

    if mode == "cbc":
        try:
            iv = base64.b64decode(payload["iv"])
        except (KeyError, ValueError) as exc:
            raise InvalidInputError(f"iv missing or not valid base64: {exc}") from exc
        if len(iv) != 16:
            raise InvalidInputError("CBC IV must be 16 bytes.")
        cipher = AES.new(key, AES.MODE_CBC, iv=iv)
        try:
            plaintext = _pkcs7_unpad(cipher.decrypt(ciphertext))
        except ValueError as exc:
            raise VerificationError(f"CBC decrypt failed: {exc}") from exc
        return {"plaintext": plaintext.decode("utf-8", errors="replace"), "mode": mode}

    if mode == "ecb":
        cipher = AES.new(key, AES.MODE_ECB)
        try:
            plaintext = _pkcs7_unpad(cipher.decrypt(ciphertext))
        except ValueError as exc:
            raise VerificationError(f"ECB decrypt failed: {exc}") from exc
        return {"plaintext": plaintext.decode("utf-8", errors="replace"), "mode": mode}

    raise UnsupportedOperationError(f"Unknown mode {mode!r}.")


def _pkcs7_pad(data: bytes) -> bytes:
    pad_len = 16 - (len(data) % 16)
    return data + bytes([pad_len]) * pad_len


def _pkcs7_unpad(data: bytes) -> bytes:
    if not data or len(data) % 16:
        raise ValueError("Ciphertext is not a multiple of the block size.")
    pad_len = data[-1]
    if not 1 <= pad_len <= 16 or data[-pad_len:] != bytes([pad_len]) * pad_len:
        raise ValueError("Invalid PKCS#7 padding.")
    return data[:-pad_len]
