"""Shared FastAPI dependencies: DB session, current user, scope enforcement."""

from __future__ import annotations

from collections.abc import AsyncIterator, Callable
from typing import Annotated

from fastapi import Depends, Request
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.exceptions import ForbiddenError, UnauthorizedError
from app.core.security import JWTError, Scope, decode_token, has_scope
from app.db.session import get_session
from app.schemas.user import TokenPayload
from app.services.storage_service import StorageService, get_storage_service

oauth2_scheme = OAuth2PasswordBearer(tokenUrl=f"{settings.api_v1_prefix}/auth/token")


async def db_session() -> AsyncIterator[AsyncSession]:
    async for session in get_session():
        yield session


def get_storage() -> StorageService:
    """Object-store client dependency (overridable in tests)."""
    return get_storage_service()


def client_ip(request: Request) -> str | None:
    """Best-effort source IP for audit logging (honours X-Forwarded-For)."""
    fwd = request.headers.get("x-forwarded-for")
    if fwd:
        return fwd.split(",")[0].strip()
    return request.client.host if request.client else None


async def current_user(token: Annotated[str, Depends(oauth2_scheme)]) -> TokenPayload:
    """Decode + validate the bearer token into a typed principal."""
    try:
        claims = decode_token(token)
    except JWTError as exc:
        raise UnauthorizedError("Invalid or expired token") from exc
    if claims.get("type") != "access":
        raise UnauthorizedError("Invalid token type")
    return TokenPayload(
        sub=claims.get("sub", ""),
        role=claims.get("role", ""),
        scopes=claims.get("scopes", []),
    )


def require_scope(scope: Scope) -> Callable[..., TokenPayload]:
    """Dependency factory enforcing a single RBAC scope (default-deny)."""

    def _checker(user: Annotated[TokenPayload, Depends(current_user)]) -> TokenPayload:
        if not has_scope(user.scopes, scope):
            raise ForbiddenError(f"Missing required scope: {scope.value}")
        return user

    return _checker


DbSession = Annotated[AsyncSession, Depends(db_session)]
CurrentUser = Annotated[TokenPayload, Depends(current_user)]
Storage = Annotated[StorageService, Depends(get_storage)]
ClientIp = Annotated["str | None", Depends(client_ip)]
