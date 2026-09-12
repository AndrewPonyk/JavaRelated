"""RSA service — OAEP encryption and PSS signatures on the secure path.

Textbook RSA (no padding) exists ONLY as an attack-gallery demo showing its
determinism and multiplicative malleability. It is never the default.
"""

from __future__ import annotations

import base64

from Crypto.Cipher import PKCS1_OAEP
from Crypto.Hash import SHA256
from Crypto.PublicKey import RSA
from Crypto.Signature import pss

from crypto_toolkit.errors import (
    InvalidInputError,
    VerificationError,
)

MIN_BITS = 2048
# OAEP-SHA256 overhead: 2*hash_len + 1 = 65 bytes
OAEP_OVERHEAD = 2 * 32 + 1


def generate_keypair(bits: int = 2048) -> dict:
    """Generate an RSA keypair; returns PEM strings (public + private)."""
    if bits < MIN_BITS:
        raise InvalidInputError(f"Use >= {MIN_BITS}-bit keys; 1024-bit RSA is broken.")
    if bits % 256 or bits > 4096:
        raise InvalidInputError("Bits must be a multiple of 256 in [2048, 4096].")
    key = RSA.generate(bits)
    return {
        "bits": bits,
        "e": key.e,
        "public_pem": key.publickey().export_key().decode(),
        "private_pem": key.export_key().decode(),
        "note": "Demo keypair — in production the private key never leaves the client/HSM.",
    }


def _import(pem: str, what: str):
    if not isinstance(pem, str) or "BEGIN" not in pem:
        raise InvalidInputError(f"{what} is not a PEM string.")
    try:
        return RSA.import_key(pem)
    except (ValueError, IndexError) as exc:
        raise InvalidInputError(f"{what} is not a valid RSA key: {exc}") from exc


def encrypt(public_pem: str, plaintext: bytes) -> dict:
    """RSA-OAEP (SHA-256) encrypt — message must fit inside one block."""
    key = _import(public_pem, "public_pem")
    if key.has_private():
        # accepted technically, but the lesson teaches public-key encryption
        raise InvalidInputError("Provide the PUBLIC key for encryption (not the private PEM).")
    max_len = key.size_in_bytes() - OAEP_OVERHEAD
    if len(plaintext) > max_len:
        raise InvalidInputError(
            f"Message too long for OAEP ({len(plaintext)} B > {max_len} B); "
            "hybrid encryption (RSA wraps an AES key) is the real-world answer."
        )
    cipher = PKCS1_OAEP.new(key, hashAlgo=SHA256)
    return {
        "scheme": "rsa-oaep-sha256",
        "ciphertext": base64.b64encode(cipher.encrypt(plaintext)).decode(),
    }


def decrypt(private_pem: str, ciphertext_b64: str) -> dict:
    key = _import(private_pem, "private_pem")
    if not key.has_private():
        raise InvalidInputError("Provided PEM is not a private key.")
    try:
        ciphertext = base64.b64decode(ciphertext_b64, validate=True)
    except ValueError as exc:
        raise InvalidInputError(f"ciphertext is not valid base64: {exc}") from exc
    cipher = PKCS1_OAEP.new(key, hashAlgo=SHA256)
    try:
        plaintext = cipher.decrypt(ciphertext)
    except ValueError as exc:
        raise VerificationError(f"OAEP decryption failed (wrong key or corrupted): {exc}") from exc
    return {"plaintext": plaintext.decode("utf-8", errors="replace")}


def sign(private_pem: str, message: bytes) -> dict:
    """RSA-PSS (SHA-256) signature."""
    key = _import(private_pem, "private_pem")
    if not key.has_private():
        raise InvalidInputError("Signing requires the private key.")
    signature = pss.new(key, salt_bytes=32).sign(SHA256.new(message))
    return {"scheme": "rsa-pss-sha256", "signature": base64.b64encode(signature).decode()}


def verify(public_pem: str, message: bytes, signature_b64: str) -> dict:
    key = _import(public_pem, "public_pem")
    try:
        signature = base64.b64decode(signature_b64, validate=True)
    except ValueError as exc:
        raise InvalidInputError(f"signature is not valid base64: {exc}") from exc
    try:
        pss.new(key, salt_bytes=32).verify(SHA256.new(message), signature)
    except (ValueError, TypeError) as exc:
        raise VerificationError(f"Signature verification failed: {exc}") from exc
    return {"verified": True, "scheme": "rsa-pss-sha256"}


# ---------------- attack gallery (demo=True path) ----------------


def textbook_malleability(public_pem: str, private_pem: str, message: bytes) -> dict:
    """ATTACK DEMO — raw RSA (m^e mod n, no padding).

    Shows two fatal properties of textbook RSA:
    1. Determinism: same message -> same ciphertext.
    2. Multiplicative malleability: E(m) * E(2) mod n decrypts to 2m,
       letting an attacker transform ciphertexts without the key.
    """
    pub = _import(public_pem, "public_pem").publickey()
    priv = _import(private_pem, "private_pem")
    if not priv.has_private():
        raise InvalidInputError("Demo needs the private key to reveal the result.")
    m = int.from_bytes(message, "big")
    if m >= pub.n:
        raise InvalidInputError("Message integer must be smaller than the modulus.")
    if 2 * m >= pub.n:
        raise InvalidInputError("Use a message < n/2 so 2*m stays in range.")

    def raw_enc(x: int) -> int:
        return pow(x, pub.e, pub.n)

    c1 = raw_enc(m)
    c2 = raw_enc(m)  # deterministic: identical
    c_doubled = (c1 * raw_enc(2)) % pub.n  # attacker needs only the public key
    decrypted = pow(c_doubled, priv.d, priv.n)
    recovered = decrypted.to_bytes((decrypted.bit_length() + 7) // 8, "big")
    try:
        recovered_display = recovered.decode("utf-8")
        recovered_hex = None
    except UnicodeDecodeError:
        recovered_display = None
        recovered_hex = recovered.hex()
    return {
        "warning": "Textbook RSA (no padding) — attack gallery only. Never use.",
        "deterministic": c1 == c2,
        "original_ciphertext_hex": format(c1, "x"),
        "attacker_ciphertext_hex": format(c_doubled, "x"),
        "attacker_needs_only_public_key": True,
        "decrypted_int": decrypted,
        "decrypted_message": recovered_display,
        "decrypted_message_hex": recovered_hex,
        "explanation": (
            "The attacker multiplied E(m) by E(2) mod n — requiring only the public key. "
            "Raw RSA decryption yields 2m: the ciphertext was modified meaningfully. "
            "OAEP padding randomizes and authenticates, defeating both properties."
        ),
        "countermeasure": "RSA-OAEP (encryption) / RSA-PSS (signatures) with >=2048-bit keys.",
    }
