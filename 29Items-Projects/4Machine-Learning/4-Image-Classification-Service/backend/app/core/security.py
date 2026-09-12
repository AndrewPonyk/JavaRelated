"""Authentication & authorization.

- Service clients authenticate with an API key (``X-API-Key`` header).
- The React console authenticates with a short-lived JWT
  (``Authorization: Bearer <token>``) issued by ``POST /auth/token``.

Scopes: ``classify``, ``taxonomy:read``, ``taxonomy:write``, ``admin``.
"""

from __future__ import annotations

import hmac
import time
from typing import Annotated, cast

import jwt
from fastapi import Depends, Header, HTTPException, status

from app.core.config import Settings, get_settings

ALL_SCOPES = {"classify", "taxonomy:read", "taxonomy:write", "admin"}


# --------------------------------------------------------------------------- API keys
def verify_api_key(x_api_key: str | None = Header(default=None)) -> str:
    """FastAPI dependency: validate ``X-API-Key`` with a constant-time comparison."""
    settings = get_settings()
    allowed = settings.allowed_api_keys

    if not allowed:
        # No keys configured: open mode for local dev only; refuse in production.
        if settings.is_production:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="API keys not configured",
            )
        return "dev"

    if x_api_key and any(hmac.compare_digest(x_api_key, k) for k in allowed):
        return x_api_key

    raise HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Invalid or missing API key",
        headers={"WWW-Authenticate": "ApiKey"},
    )


# ------------------------------------------------------------------------------- JWT
def create_access_token(
    subject: str,
    scopes: list[str],
    settings: Settings | None = None,
    expires_in: int = 3600,
) -> str:
    settings = settings or get_settings()
    now = int(time.time())
    payload = {
        "sub": subject,
        "scopes": scopes,
        "iat": now,
        "exp": now + expires_in,
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)


def decode_jwt(token: str, settings: Settings | None = None) -> dict[str, object]:
    """Decode and verify a console JWT (signature + expiry)."""
    settings = settings or get_settings()
    try:
        return jwt.decode(token, settings.jwt_secret, algorithms=[settings.jwt_algorithm])
    except jwt.ExpiredSignatureError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="Token expired"
        ) from exc
    except jwt.PyJWTError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token"
        ) from exc


def get_current_claims(
    authorization: str | None = Header(default=None),
) -> dict[str, object]:
    """Extract and verify the bearer token, returning its claims."""
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing bearer token",
            headers={"WWW-Authenticate": "Bearer"},
        )
    token = authorization.split(" ", 1)[1]
    return decode_jwt(token)


def require_scope(scope: str):
    """Return a dependency enforcing that the caller's token holds ``scope``."""

    def _dependency(
        claims: Annotated[dict[str, object], Depends(get_current_claims)],
    ) -> dict[str, object]:
        token_scopes = set(cast("list[str]", claims.get("scopes", [])))
        if scope not in token_scopes and "admin" not in token_scopes:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Missing required scope: {scope}",
            )
        return claims

    return _dependency


def _api_key_valid(x_api_key: str | None, allowed: set[str]) -> bool:
    if not allowed:
        return True  # open dev mode (no keys configured)
    if not x_api_key:
        return False
    return any(hmac.compare_digest(x_api_key, k) for k in allowed)


def authorize(scope: str):
    """Dependency allowing EITHER a valid API key OR a JWT carrying ``scope``.

    Service clients authenticate with ``X-API-Key``; the console uses a bearer token.
    """

    def _dependency(
        x_api_key: str | None = Header(default=None),
        authorization: str | None = Header(default=None),
    ) -> str:
        settings = get_settings()
        if _api_key_valid(x_api_key, settings.allowed_api_keys) and (
            x_api_key or not settings.is_production
        ):
            return "api_key"
        if authorization and authorization.lower().startswith("bearer "):
            claims = decode_jwt(authorization.split(" ", 1)[1], settings)
            token_scopes = set(cast("list[str]", claims.get("scopes", [])))
            if scope in token_scopes or "admin" in token_scopes:
                return str(claims.get("sub", "user"))
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Missing required scope: {scope}",
            )
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Provide a valid API key or bearer token",
            headers={"WWW-Authenticate": "Bearer"},
        )

    return _dependency
