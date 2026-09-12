"""JWT helpers. In production, validate tokens against an OIDC provider
(e.g. AWS Cognito / Okta) using JWKS instead of a shared secret."""

from datetime import UTC, datetime, timedelta
from typing import Any

import jwt

from app.core.config import get_settings


def create_access_token(subject: str, extra_claims: dict[str, Any] | None = None) -> str:
    settings = get_settings()
    now = datetime.now(UTC)
    payload: dict[str, Any] = {
        "sub": subject,
        "iat": now,
        "exp": now + timedelta(minutes=settings.access_token_expire_minutes),
    }
    if extra_claims:
        payload.update(extra_claims)
    return jwt.encode(payload, settings.jwt_secret_key, algorithm=settings.jwt_algorithm)


def decode_access_token(token: str) -> dict[str, Any]:
    """Decode and verify a JWT. Raises ``jwt.PyJWTError`` on failure."""
    settings = get_settings()
    return jwt.decode(token, settings.jwt_secret_key, algorithms=[settings.jwt_algorithm])
