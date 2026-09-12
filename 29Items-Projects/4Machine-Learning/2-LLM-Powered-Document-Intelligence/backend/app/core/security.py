"""Authentication & authorization.

Production verifies RS256 JWTs against the IdP's JWKS (``settings.jwt_jwks_url``) and
extracts the ``tenant`` claim that scopes all data access. In development (no JWKS
configured) tokens are decoded without signature verification so the app and tests run
without an IdP — and a non-JWT bearer (e.g. the frontend's ``dev-token``) maps to a
default tenant.
"""

from __future__ import annotations

import time
from dataclasses import dataclass
from functools import lru_cache

import jwt
from jwt import PyJWKClient

from app.core.config import settings


@dataclass(frozen=True)
class Principal:
    """The authenticated caller and their tenant scope."""

    subject: str
    tenant_id: str
    scopes: tuple[str, ...] = ()

    def has_scope(self, scope: str) -> bool:
        return scope in self.scopes


class AuthError(Exception):
    """Raised when a token is missing, malformed, or fails verification."""


_DEV_PRINCIPAL = Principal(
    subject="dev-user",
    tenant_id="dev-tenant",
    scopes=("documents:read", "documents:write", "query:run"),
)


@lru_cache
def _jwk_client() -> PyJWKClient:
    return PyJWKClient(settings.jwt_jwks_url)


def _claims_to_principal(claims: dict) -> Principal:
    tenant = claims.get("tenant") or claims.get("tenant_id")
    if not tenant:
        raise AuthError("token missing required 'tenant' claim")
    scopes = claims.get("scopes", [])
    if isinstance(scopes, str):
        scopes = scopes.split()
    return Principal(
        subject=str(claims.get("sub", "unknown")),
        tenant_id=str(tenant),
        scopes=tuple(scopes),
    )


def verify_token(token: str) -> Principal:
    """Verify a bearer JWT and return the :class:`Principal`."""
    if not token:
        raise AuthError("missing bearer token")

    # ── Production: verify signature against the IdP JWKS ──
    if settings.jwt_jwks_url:
        try:
            signing_key = _jwk_client().get_signing_key_from_jwt(token)
            claims = jwt.decode(
                token,
                signing_key.key,
                algorithms=["RS256"],
                audience=settings.jwt_audience,
                issuer=settings.jwt_issuer or None,
                options={"require": ["exp"]},
            )
        except jwt.PyJWTError as exc:
            raise AuthError(f"token verification failed: {exc}") from exc
        return _claims_to_principal(claims)

    # ── Production must be configured with a JWKS — fail closed, never accept
    #    unsigned/dev tokens when APP_ENV=production. ──
    if settings.is_production:
        raise AuthError("authentication is not configured (JWT_JWKS_URL is required in production)")

    # ── Development / test: accept locally-minted or unsigned tokens ──
    try:
        claims = jwt.decode(token, options={"verify_signature": False})
        return _claims_to_principal(claims)
    except jwt.PyJWTError:
        # A non-JWT bearer (e.g. the frontend's "dev-token") → default dev principal.
        return _DEV_PRINCIPAL


def make_dev_token(
    tenant_id: str,
    subject: str = "dev-user",
    scopes: tuple[str, ...] = ("documents:read", "documents:write", "query:run"),
    ttl_seconds: int = 3600,
) -> str:
    """Mint an HS256 JWT for local development and tests."""
    now = int(time.time())
    payload = {
        "sub": subject,
        "tenant": tenant_id,
        "scopes": list(scopes),
        "iat": now,
        "exp": now + ttl_seconds,
        "aud": settings.jwt_audience,
    }
    return jwt.encode(payload, settings.jwt_dev_secret, algorithm="HS256")
