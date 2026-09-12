"""SHA-3 service — Keccak-family hashing via PyCryptodome."""

from __future__ import annotations

import binascii

from Crypto.Hash import SHA3_256, SHA3_384, SHA3_512

from crypto_toolkit.errors import InvalidInputError

_HASHERS = {
    "sha3-256": SHA3_256,
    "sha3-384": SHA3_384,
    "sha3-512": SHA3_512,
}


def digest(message: bytes, algorithm: str = "sha3-256") -> dict:
    if algorithm not in _HASHERS:
        raise InvalidInputError(f"Unknown algorithm {algorithm!r}; expected one of {sorted(_HASHERS)}.")
    hasher = _HASHERS[algorithm].new(data=message)
    hex_digest = binascii.hexlify(hasher.digest()).decode()
    return {
        "algorithm": algorithm,
        "digest_hex": hex_digest,
        "digest_bits": hasher.digest_size * 8,
    }
