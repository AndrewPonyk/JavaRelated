"""HKDF primitives per RFC 5869 + the TLS 1.3 key schedule labels (RFC 8446 §7.1).

Implemented explicitly (hmac stdlib) so the lesson can show every byte, and
because HKDF-Expand-Label needs expand-only which convenience wrappers hide.
"""

from __future__ import annotations

import hashlib
import hmac


def hkdf_extract(salt: bytes, ikm: bytes, hashmod=hashlib.sha256) -> bytes:
    if not salt:
        salt = b"\x00" * hashmod().digest_size
    return hmac.new(salt, ikm, hashmod).digest()


def hkdf_expand(prk: bytes, info: bytes, length: int, hashmod=hashlib.sha256) -> bytes:
    hash_len = hashmod().digest_size
    if length > 255 * hash_len:
        raise ValueError("HKDF-Expand length too large.")
    t = b""
    okm = b""
    counter = 1
    while len(okm) < length:
        t = hmac.new(prk, t + info + bytes([counter]), hashmod).digest()
        okm += t
        counter += 1
    return okm[:length]


def hkdf_expand_label(
    secret: bytes, label: str, context: bytes, length: int, hashmod=hashlib.sha256
) -> bytes:
    """RFC 8446 §7.1 — HkdfLabel{length, "tls13 "+label, context}."""
    full_label = b"tls13 " + label.encode("ascii")
    if not 6 <= len(full_label) <= 255:
        raise ValueError("Label length out of range.")
    if len(context) > 255:
        raise ValueError("Context too long.")
    hkdf_label = (
        length.to_bytes(2, "big") + bytes([len(full_label)]) + full_label + bytes([len(context)]) + context
    )
    return hkdf_expand(secret, hkdf_label, length, hashmod)


def derive_secret(secret: bytes, label: str, messages: bytes, hashmod=hashlib.sha256) -> bytes:
    """Derive-Secret(Secret, Label, Messages) = Expand-Label(Secret, Label, Hash(Messages), Hash.length)."""
    return hkdf_expand_label(secret, label, hashmod(messages).digest(), hashmod().digest_size, hashmod)
