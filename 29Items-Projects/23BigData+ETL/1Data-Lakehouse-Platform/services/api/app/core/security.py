"""Authentication and authorization.

Two explicit modes, selected by configuration:

* **Enforced** (`AUTH_JWKS_URL` set): requests must carry a Bearer JWT. The token
  is verified against the IdP's JWKS (RS256), including signature, expiry and
  audience; roles come from the configured claim.
* **Development** (`AUTH_JWKS_URL` empty): every request acts as a local
  platform-admin. This is a deliberate, documented mode for the compose stack —
  production environments always set AUTH_JWKS_URL.

Mutating routes additionally require a writer role via `require_writer`.
"""

from __future__ import annotations

from dataclasses import dataclass
from functools import lru_cache

import jwt
from fastapi import Depends, Header, HTTPException, status

from app.core.config import Settings, get_settings

WRITER_ROLES = frozenset({"platform-admin", "data-engineer"})


@dataclass(frozen=True)
class Principal:
    subject: str
    roles: tuple[str, ...]

    def has_any_role(self, allowed: frozenset[str]) -> bool:
        return bool(allowed.intersection(self.roles))


class TokenVerifier:
    """Verifies RS256 JWTs against a JWKS endpoint (keys cached by PyJWKClient)."""

    def __init__(self, jwks_url: str, audience: str, roles_claim: str) -> None:
        self._jwks_client = jwt.PyJWKClient(jwks_url, cache_keys=True)
        self._audience = audience
        self._roles_claim = roles_claim

    def verify(self, token: str) -> Principal:
        signing_key = self._jwks_client.get_signing_key_from_jwt(token)
        claims = jwt.decode(
            token,
            key=signing_key.key,
            algorithms=["RS256"],
            audience=self._audience,
            options={"require": ["exp", "sub"]},
        )
        raw_roles = claims.get(self._roles_claim) or []
        if isinstance(raw_roles, str):  # some IdPs emit space-separated strings
            raw_roles = raw_roles.split()
        return Principal(subject=claims["sub"], roles=tuple(raw_roles))


@lru_cache
def get_token_verifier() -> TokenVerifier:
    settings: Settings = get_settings()
    return TokenVerifier(
        jwks_url=settings.auth_jwks_url,
        audience=settings.auth_audience,
        roles_claim=settings.auth_roles_claim,
    )


def get_current_principal(authorization: str | None = Header(default=None)) -> Principal:
    settings = get_settings()

    if not settings.auth_jwks_url:
        return Principal(subject="dev@localhost", roles=("platform-admin",))

    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(
            status.HTTP_401_UNAUTHORIZED,
            detail="Missing bearer token",
            headers={"WWW-Authenticate": "Bearer"},
        )

    token = authorization.removeprefix("Bearer ").strip()
    try:
        return get_token_verifier().verify(token)
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status.HTTP_401_UNAUTHORIZED,
            detail="Token has expired",
            headers={"WWW-Authenticate": "Bearer"},
        ) from None
    except jwt.PyJWKClientConnectionError:
        # The IdP being unreachable is our outage, not the caller's fault.
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Authentication service unavailable, retry shortly",
        ) from None
    except (jwt.InvalidTokenError, jwt.PyJWKClientError) as exc:
        raise HTTPException(
            status.HTTP_401_UNAUTHORIZED,
            detail=f"Invalid token: {exc}",
            headers={"WWW-Authenticate": "Bearer"},
        ) from None


def require_writer(principal: Principal = Depends(get_current_principal)) -> Principal:
    """Gate for mutating endpoints: platform-admin or data-engineer only."""
    if not principal.has_any_role(WRITER_ROLES):
        raise HTTPException(
            status.HTTP_403_FORBIDDEN,
            detail=f"Requires one of roles: {', '.join(sorted(WRITER_ROLES))}",
        )
    return principal
