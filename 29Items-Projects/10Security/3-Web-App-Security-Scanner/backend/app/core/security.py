"""Authentication & authorization primitives.

Deliberately stdlib-only crypto (no python-jose/passlib):
  - JWT: HS256 via hmac/hashlib — auditable, dependency-free
  - Passwords: PBKDF2-HMAC-SHA256, 310k iterations (OWASP-banded)
  - API keys: 256-bit secrets, SHA-256-hashed at rest, prefix-indexed

Roles: viewer < scanner < admin (RBAC via require_role).
"""

from __future__ import annotations

import base64
import binascii
import hashlib
import hmac
import json
import secrets
import time
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from typing import Literal, cast

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.config import settings

Role = Literal["viewer", "scanner", "admin"]
_ROLE_RANK: dict[str, int] = {"viewer": 0, "scanner": 1, "admin": 2}

_bearer = HTTPBearer(auto_error=False)

_PBKDF2_ITERATIONS = 310_000
_API_KEY_PREFIX = "wss_"

# ── password hashing (PBKDF2-HMAC-SHA256) ──────────────────────────────


def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, _PBKDF2_ITERATIONS)
    return "$".join(
        [
            "pbkdf2_sha256",
            str(_PBKDF2_ITERATIONS),
            base64.b64encode(salt).decode(),
            base64.b64encode(digest).decode(),
        ]
    )


def verify_password(password: str, hashed: str) -> bool:
    try:
        scheme, iterations, salt_b64, digest_b64 = hashed.split("$", 3)
    except ValueError:
        return False
    if scheme != "pbkdf2_sha256":
        return False
    salt = base64.b64decode(salt_b64)
    expected = base64.b64decode(digest_b64)
    actual = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, int(iterations))
    return hmac.compare_digest(actual, expected)


# ── JWT (HS256) ────────────────────────────────────────────────────────


@dataclass(frozen=True)
class Principal:
    """Authenticated caller — human user (JWT) or CI service account (API key)."""

    id: str
    role: Role
    auth_type: Literal["jwt", "api_key"] = "jwt"


class TokenError(ValueError):
    """Raised when a token is malformed, expired, or has a bad signature."""


def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def _b64url_decode(data: str) -> bytes:
    padding = "=" * (-len(data) % 4)
    return base64.urlsafe_b64decode(data + padding)


def _jwt_encode(payload: dict) -> str:
    header = _b64url(b'{"alg":"HS256","typ":"JWT"}')
    body = _b64url(json.dumps(payload, separators=(",", ":")).encode())
    signing_input = f"{header}.{body}".encode()
    signature = hmac.new(settings.SECRET_KEY.encode(), signing_input, hashlib.sha256).digest()
    return f"{header}.{body}.{_b64url(signature)}"


def _jwt_decode(token: str) -> dict:
    try:
        header_b64, body_b64, signature_b64 = token.split(".")
    except ValueError as exc:
        raise TokenError("malformed token") from exc
    signing_input = f"{header_b64}.{body_b64}".encode()
    expected = hmac.new(settings.SECRET_KEY.encode(), signing_input, hashlib.sha256).digest()
    try:
        signature = _b64url_decode(signature_b64)
    except (ValueError, binascii.Error) as exc:
        raise TokenError("malformed signature") from exc
    if not hmac.compare_digest(expected, signature):
        raise TokenError("bad signature")
    try:
        payload = json.loads(_b64url_decode(body_b64))
    except (ValueError, json.JSONDecodeError) as exc:
        raise TokenError("malformed payload") from exc
    if payload.get("exp") is not None and payload["exp"] < time.time():
        raise TokenError("token expired")
    return payload


def create_access_token(user_id: int, role: Role) -> str:
    now = datetime.now(UTC)
    payload = {
        "sub": str(user_id),
        "role": role,
        "type": "access",
        "iat": int(now.timestamp()),
        "exp": int((now + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)).timestamp()),
        "jti": uuid.uuid4().hex,
    }
    return _jwt_encode(payload)


def create_refresh_token(user_id: int, role: Role) -> str:
    now = datetime.now(UTC)
    payload = {
        "sub": str(user_id),
        "role": role,
        "type": "refresh",
        "iat": int(now.timestamp()),
        "exp": int((now + timedelta(days=settings.REFRESH_TOKEN_EXPIRE_DAYS)).timestamp()),
        "jti": uuid.uuid4().hex,
    }
    return _jwt_encode(payload)


def decode_token(token: str, *, expected_type: str = "access") -> dict:
    payload = _jwt_decode(token)
    if payload.get("type") != expected_type:
        raise TokenError(f"expected {expected_type} token")
    return payload


# ── API keys ───────────────────────────────────────────────────────────


def generate_api_key() -> tuple[str, str, str]:
    """Return (full_secret, prefix, sha256_hash). Secret shown exactly once."""
    secret = _API_KEY_PREFIX + secrets.token_urlsafe(32)
    return secret, api_key_prefix(secret), hashlib.sha256(secret.encode()).hexdigest()


def api_key_prefix(secret: str) -> str:
    """The stored lookup prefix — single source of truth for its length."""
    return secret[: len(_API_KEY_PREFIX) + 6]


def hash_api_key(secret: str) -> str:
    return hashlib.sha256(secret.encode()).hexdigest()


# ── FastAPI dependency ─────────────────────────────────────────────────


async def get_current_principal(
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
) -> Principal:
    """Resolve the caller from a Bearer JWT or an API key (wss_…)."""
    from app.db.session import SessionFactory
    from app.models.api_key import ApiKey

    if credentials is None:
        raise HTTPException(
            status.HTTP_401_UNAUTHORIZED,
            "Not authenticated",
            headers={"WWW-Authenticate": "Bearer"},
        )
    token = credentials.credentials

    if token.startswith(_API_KEY_PREFIX):
        async with SessionFactory() as session:
            api_key = await ApiKey.find_by_secret(session, token)
            if api_key is None:
                raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid API key")
            # api_key.role is a validated DB value; the cast documents the
            # Literal narrowing for the type checker
            return Principal(
                id=f"apikey:{api_key.id}", role=cast(Role, api_key.role), auth_type="api_key"
            )

    try:
        payload = decode_token(token, expected_type="access")
    except TokenError as exc:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, str(exc)) from exc
    role = payload.get("role", "viewer")
    if role not in _ROLE_RANK:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Token carries unknown role")
    return Principal(id=payload["sub"], role=role)


def role_rank(role: Role) -> int:
    """Public accessor for the role hierarchy (viewer < scanner < admin)."""
    return _ROLE_RANK.get(role, -1)


def require_role(minimum: Role):
    """FastAPI dependency enforcing a minimum role for an endpoint."""

    def _checker(principal: Principal = Depends(get_current_principal)) -> Principal:
        if _ROLE_RANK[principal.role] < _ROLE_RANK[minimum]:
            raise HTTPException(
                status.HTTP_403_FORBIDDEN,
                f"Requires role '{minimum}' (you are '{principal.role}')",
            )
        return principal

    return _checker
