"""Authentication primitives: password hashing and JWT access tokens.

Kept framework-agnostic (no FastAPI imports) so it can be unit-tested in
isolation. The FastAPI wiring (OAuth2 scheme, ``get_current_user``) lives in
``app.api.deps``.

Uses PyJWT (actively maintained) rather than python-jose, and only the
symmetric HS256 algorithm with an explicit allow-list on decode to avoid
algorithm-confusion attacks.
"""

from __future__ import annotations

from datetime import UTC, datetime, timedelta

import jwt
from passlib.context import CryptContext

from app.core.config import get_settings

ALGORITHM = "HS256"
_pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def hash_password(plain_password: str) -> str:
    return _pwd_context.hash(plain_password)


def verify_password(plain_password: str, hashed_password: str) -> bool:
    return _pwd_context.verify(plain_password, hashed_password)


def create_access_token(subject: str | int, expires_delta: timedelta | None = None) -> str:
    """Issue a signed JWT whose ``sub`` claim identifies the user."""
    settings = get_settings()
    now = datetime.now(UTC)
    expire = now + (expires_delta or timedelta(minutes=settings.access_token_expire_minutes))
    payload = {"sub": str(subject), "exp": expire, "iat": now}
    return jwt.encode(payload, settings.secret_key, algorithm=ALGORITHM)


def decode_access_token(token: str) -> str | None:
    """Return the token subject (user id) or ``None`` if invalid/expired."""
    settings = get_settings()
    try:
        payload = jwt.decode(token, settings.secret_key, algorithms=[ALGORITHM])
    except jwt.PyJWTError:
        return None
    return payload.get("sub")
