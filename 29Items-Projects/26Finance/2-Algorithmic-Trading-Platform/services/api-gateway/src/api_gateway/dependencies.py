"""FastAPI dependencies: DB session per request and auth/role checks."""

from __future__ import annotations

from collections.abc import AsyncIterator
from typing import ClassVar

import jwt
from fastapi import Depends, Header, HTTPException, Request, status
from sqlalchemy.ext.asyncio import AsyncSession


async def get_session(request: Request) -> AsyncIterator[AsyncSession]:
    """Yield a transactional session; commit on success, rollback on error."""
    factory = request.app.state.session_factory
    session: AsyncSession = factory()
    try:
        yield session
        await session.commit()
    except Exception:
        await session.rollback()
        raise
    finally:
        await session.close()


class Principal:
    """Authenticated caller with a role (viewer < trader < admin)."""

    _ORDER: ClassVar[dict[str, int]] = {"viewer": 0, "trader": 1, "admin": 2}

    def __init__(self, subject: str, role: str) -> None:
        self.subject = subject
        self.role = role

    def has_at_least(self, role: str) -> bool:
        return self._ORDER.get(self.role, -1) >= self._ORDER[role]


async def get_principal(
    request: Request,
    authorization: str | None = Header(default=None),
) -> Principal:
    """Resolve and authenticate the caller from a Bearer JWT.

    When auth is disabled (dev/tests) the caller is an admin. When enabled, the
    ``Authorization: Bearer <jwt>`` token is verified (signature + expiry, and
    audience if configured) and the ``role`` claim drives RBAC (ARCHITECTURE §2.5).
    """
    state = request.app.state
    if not state.auth_enabled:
        return Principal(subject="dev", role="admin")

    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail="missing bearer token")
    token = authorization.split(" ", 1)[1].strip()

    try:
        payload = jwt.decode(
            token,
            state.jwt_secret,
            algorithms=[state.jwt_algorithm],
            audience=state.jwt_audience,
            options={"verify_aud": state.jwt_audience is not None, "require": ["exp"]},
        )
    except jwt.ExpiredSignatureError:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail="token expired") from None
    except jwt.InvalidTokenError:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail="invalid token") from None

    role = str(payload.get("role") or "viewer")
    if role not in Principal._ORDER:
        raise HTTPException(status.HTTP_403_FORBIDDEN, detail=f"unknown role: {role}")
    return Principal(subject=str(payload.get("sub", "unknown")), role=role)


def require_role(role: str):
    """Dependency factory enforcing a minimum role."""

    async def _checker(principal: Principal = Depends(get_principal)) -> Principal:
        if not principal.has_at_least(role):
            raise HTTPException(status.HTTP_403_FORBIDDEN, detail=f"requires role >= {role}")
        return principal

    return _checker
