"""Authentication & authorization.

JWTs are verified at the edge; RBAC scopes gate sensitive actions (e.g.
promoting a model to Production). A ``TokenVerifier`` interface keeps the
signature scheme pluggable: this module ships a self-contained HS256 verifier
(stdlib only) for service tokens / local dev; production OIDC (RS256 + JWKS)
implements the same interface.
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import json
import time
from dataclasses import dataclass, field
from typing import Protocol

from src.core.errors import AuthenticationError, AuthorizationError


@dataclass(frozen=True)
class Principal:
    """The authenticated caller resolved from a validated token."""

    subject: str
    email: str
    scopes: frozenset[str] = field(default_factory=frozenset)

    def has(self, scope: str) -> bool:
        return scope in self.scopes or "admin:*" in self.scopes

    def require(self, scope: str) -> None:
        """Raise :class:`AuthorizationError` (403) unless the scope is held."""
        if not self.has(scope):
            raise AuthorizationError(f"missing required scope: {scope}")


# --------------------------------------------------------------------------- #
# Minimal JWT (HS256) helpers — stdlib only, no third-party dependency.
# --------------------------------------------------------------------------- #
def _b64url_encode(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def _b64url_decode(segment: str) -> bytes:
    padding = "=" * (-len(segment) % 4)
    return base64.urlsafe_b64decode(segment + padding)


def encode_hs256(claims: dict[str, object], secret: str) -> str:
    """Encode a signed HS256 JWT. Used for service tokens and tests."""
    header = {"alg": "HS256", "typ": "JWT"}
    h = _b64url_encode(json.dumps(header, separators=(",", ":")).encode())
    p = _b64url_encode(json.dumps(claims, separators=(",", ":")).encode())
    signing_input = f"{h}.{p}".encode()
    sig = hmac.new(secret.encode(), signing_input, hashlib.sha256).digest()
    return f"{h}.{p}.{_b64url_encode(sig)}"


class TokenVerifier(Protocol):
    """Verifies a bearer token and returns its :class:`Principal`."""

    def verify(self, token: str) -> Principal: ...


class HS256TokenVerifier:
    """Verify HS256 JWTs against a shared secret with full claim validation."""

    def __init__(self, secret: str, audience: str, issuer: str = "") -> None:
        if not secret:
            raise ValueError("HS256 verifier requires a non-empty secret")
        self._secret = secret
        self._audience = audience
        self._issuer = issuer

    def verify(self, token: str) -> Principal:
        try:
            header_b64, payload_b64, sig_b64 = token.split(".")
        except ValueError as exc:
            raise AuthenticationError("malformed token") from exc

        # Constant-time signature check (prevents alg-confusion / forgery).
        signing_input = f"{header_b64}.{payload_b64}".encode()
        expected = hmac.new(self._secret.encode(), signing_input, hashlib.sha256).digest()
        try:
            provided = _b64url_decode(sig_b64)
        except Exception as exc:  # noqa: BLE001
            raise AuthenticationError("invalid token encoding") from exc
        if not hmac.compare_digest(expected, provided):
            raise AuthenticationError("invalid token signature")

        try:
            claims = json.loads(_b64url_decode(payload_b64))
        except Exception as exc:  # noqa: BLE001
            raise AuthenticationError("invalid token payload") from exc

        self._validate_claims(claims)
        return Principal(
            subject=str(claims.get("sub", "")),
            email=str(claims.get("email", "")),
            scopes=_parse_scopes(claims),
        )

    def _validate_claims(self, claims: dict[str, object]) -> None:
        exp = claims.get("exp")
        if exp is not None and time.time() >= float(exp):  # type: ignore[arg-type]
            raise AuthenticationError("token expired")
        if self._audience and claims.get("aud") != self._audience:
            raise AuthenticationError("invalid token audience")
        if self._issuer and claims.get("iss") != self._issuer:
            raise AuthenticationError("invalid token issuer")
        if not claims.get("sub"):
            raise AuthenticationError("token missing subject")


def _parse_scopes(claims: dict[str, object]) -> frozenset[str]:
    """Accept either OAuth space-delimited ``scope`` or a ``scopes`` list."""
    raw = claims.get("scope") or claims.get("scopes") or []
    if isinstance(raw, str):
        return frozenset(raw.split())
    if isinstance(raw, (list, tuple)):
        return frozenset(str(s) for s in raw)
    return frozenset()
