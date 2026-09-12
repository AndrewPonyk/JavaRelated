"""AuthN/AuthZ primitives: password hashing, JWT, and RBAC scopes.

Keeps crypto in one place. Endpoints depend on `require_scope(...)` (see
api/deps.py) rather than importing these directly.
"""

from __future__ import annotations

from datetime import UTC, datetime, timedelta
from enum import StrEnum
from typing import Any

from jose import JWTError, jwt
from passlib.context import CryptContext

from app.core.config import settings

_pwd = CryptContext(schemes=["bcrypt"], deprecated="auto")


class Role(StrEnum):
    """Coarse roles; map to fine-grained scopes below."""

    RADIOLOGIST = "radiologist"
    TECHNOLOGIST = "technologist"
    REFERRING = "referring_physician"
    ADMIN = "admin"


class Scope(StrEnum):
    STUDY_READ = "study:read"
    STUDY_WRITE = "study:write"
    ML_READ = "ml:read"
    ML_TRIGGER = "ml:trigger"
    ADMIN = "admin:*"


# Which scopes each role is granted. Default-deny everywhere else.
ROLE_SCOPES: dict[Role, set[Scope]] = {
    Role.RADIOLOGIST: {Scope.STUDY_READ, Scope.STUDY_WRITE, Scope.ML_READ, Scope.ML_TRIGGER},
    Role.TECHNOLOGIST: {Scope.STUDY_READ, Scope.STUDY_WRITE},
    Role.REFERRING: {Scope.STUDY_READ, Scope.ML_READ},
    Role.ADMIN: {Scope.ADMIN},
}


def hash_password(plain: str) -> str:
    return _pwd.hash(plain)


def verify_password(plain: str, hashed: str) -> bool:
    return _pwd.verify(plain, hashed)


def create_access_token(subject: str, role: Role) -> str:
    """Mint a short-lived JWT carrying subject + role-derived scopes."""
    now = datetime.now(UTC)
    scopes = sorted(s.value for s in ROLE_SCOPES.get(role, set()))
    payload: dict[str, Any] = {
        "sub": subject,
        "role": role.value,
        "scopes": scopes,
        "iat": now,
        "exp": now + timedelta(minutes=settings.access_token_expire_minutes),
        "type": "access",
    }
    return jwt.encode(payload, settings.jwt_secret_key, algorithm=settings.jwt_algorithm)


def decode_token(token: str) -> dict[str, Any]:
    """Decode + verify a JWT. Raises JWTError on invalid/expired tokens."""
    return jwt.decode(token, settings.jwt_secret_key, algorithms=[settings.jwt_algorithm])


def has_scope(token_scopes: list[str], required: Scope) -> bool:
    return Scope.ADMIN.value in token_scopes or required.value in token_scopes


__all__ = [
    "Role",
    "Scope",
    "ROLE_SCOPES",
    "hash_password",
    "verify_password",
    "create_access_token",
    "decode_token",
    "has_scope",
    "JWTError",
]
