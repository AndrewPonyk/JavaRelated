"""Argon2id password hashing with explicit, benchmarked presets.

Library defaults drift with hardware; we pin (time_cost, memory_cost,
parallelism) triples so the lesson shows real trade-offs. Argon2id is the
single mode exposed — never Argon2i alone (too fast for GPUs) nor Argon2d
(side-channel-prone) for password storage.
"""

from __future__ import annotations

from argon2 import PasswordHasher
from argon2.exceptions import VerifyMismatchError

from crypto_toolkit.errors import InvalidInputError, VerificationError

# (time_cost, memory_cost KiB, parallelism) — pin, don't trust defaults
PRESETS = {
    "interactive": (1, 19456, 2),  # OWASP baseline, ~50 ms
    "moderate": (3, 65536, 4),  # server-side accounts
    "paranoid": (8, 262144, 8),  # rate-limit this in the API layer!
}


def _hasher(preset: str) -> PasswordHasher:
    try:
        t, m, p = PRESETS[preset]
    except KeyError:
        raise InvalidInputError(f"Unknown preset {preset!r}; expected one of {sorted(PRESETS)}.") from None
    return PasswordHasher(time_cost=t, memory_cost=m, parallelism=p)


def hash_password(password: str, preset: str = "interactive") -> dict:
    if not password or len(password) > 1024:
        raise InvalidInputError("Password must be 1–1024 characters.")
    ph = _hasher(preset)
    return {"preset": preset, "hash": ph.hash(password)}


def verify_password(hash_string: str, password: str) -> dict:
    try:
        PasswordHasher().verify(hash_string, password)  # params read from the hash itself
    except VerifyMismatchError as exc:
        raise VerificationError("Password does not match hash.") from exc
    except Exception as exc:  # malformed hash string
        raise InvalidInputError(f"Not a valid Argon2 hash: {exc}") from exc
    return {"verified": True}
