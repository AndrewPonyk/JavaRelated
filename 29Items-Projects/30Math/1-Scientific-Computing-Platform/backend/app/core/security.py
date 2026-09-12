"""JWT issuing/verification and password hashing.

Password hashing uses stdlib ``hashlib.scrypt`` (no extra dependency, memory-
hard). Access tokens are short-lived and stateless; refresh tokens rotate and
are revocable server-side via the refresh_tokens table
(docs/ARCHITECTURE.md §2.5).
"""

from __future__ import annotations

import hashlib
import hmac
import os
import time
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any

from app.core.config import Settings

_SCRYPT_N, _SCRYPT_R, _SCRYPT_P = 2**14, 8, 1


class TokenError(Exception):
    """Raised for any invalid/expired/malformed token. Message is user-safe."""


@dataclass(frozen=True)
class IssuedToken:
    token: str
    jti: str
    expires_at: datetime


def create_access_token(*, subject: str, role: str, settings: Settings) -> str:
    return _encode(
        subject=subject,
        token_type="access",
        ttl_seconds=settings.access_token_ttl_seconds,
        settings=settings,
        extra={"role": role},
    ).token


def create_refresh_token(*, subject: str, settings: Settings) -> IssuedToken:
    """Refresh tokens carry no role (re-read from the DB on refresh) and are
    tracked server-side by jti for rotation/revocation."""
    return _encode(
        subject=subject,
        token_type="refresh",
        ttl_seconds=settings.refresh_token_ttl_seconds,
        settings=settings,
        extra={},
    )


def decode_token(token: str, settings: Settings, *, expected_type: str) -> dict[str, Any]:
    import jwt

    try:
        payload = jwt.decode(
            token,
            settings.jwt_secret_key,
            algorithms=[settings.jwt_algorithm],  # explicit list — never trust the header
            options={"require": ["sub", "exp", "iat", "jti"]},
        )
    except jwt.PyJWTError as exc:
        raise TokenError("Invalid or expired token.") from exc
    if payload.get("typ") != expected_type:
        raise TokenError(f"Expected a {expected_type} token.")
    return payload


def _encode(
    *, subject: str, token_type: str, ttl_seconds: int, settings: Settings, extra: dict
) -> IssuedToken:
    import jwt

    now = int(time.time())
    jti = uuid.uuid4().hex
    payload: dict[str, Any] = {
        "sub": subject,
        "iat": now,
        "exp": now + ttl_seconds,
        "jti": jti,
        "typ": token_type,
        **extra,
    }
    token = jwt.encode(payload, settings.jwt_secret_key, algorithm=settings.jwt_algorithm)
    return IssuedToken(
        token=token,
        jti=jti,
        expires_at=datetime.fromtimestamp(now + ttl_seconds, tz=UTC),
    )


def hash_password(password: str) -> str:
    salt = os.urandom(16)
    digest = hashlib.scrypt(password.encode(), salt=salt, n=_SCRYPT_N, r=_SCRYPT_R, p=_SCRYPT_P)
    return f"scrypt${_SCRYPT_N}${_SCRYPT_R}${_SCRYPT_P}${salt.hex()}${digest.hex()}"


def verify_password(password: str, stored: str) -> bool:
    try:
        scheme, n, r, p, salt_hex, digest_hex = stored.split("$")
        if scheme != "scrypt":
            return False
        candidate = hashlib.scrypt(
            password.encode(), salt=bytes.fromhex(salt_hex), n=int(n), r=int(r), p=int(p)
        )
        return hmac.compare_digest(candidate.hex(), digest_hex)
    except (ValueError, TypeError):
        return False
