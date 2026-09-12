"""FastAPI dependency wiring: settings, DB sessions, authenticated user.

Access tokens are short-lived and verified statelessly; refresh tokens are the
revocable credential (see auth_service). ``OptionalUser`` exists for endpoints
that behave differently when authenticated (e.g. solve → async escalation).
"""

from __future__ import annotations

from typing import Annotated
from uuid import UUID

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings, get_settings
from app.core.security import TokenError, decode_token
from app.db.session import get_db_session
from app.schemas.common import UserClaims

SettingsDep = Annotated[Settings, Depends(get_settings)]
SessionDep = Annotated[AsyncSession, Depends(get_db_session)]

_bearer = HTTPBearer(auto_error=False)

_CredentialsDep = Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer)]


def _claims_from(credentials: HTTPAuthorizationCredentials, settings: Settings) -> UserClaims:
    try:
        payload = decode_token(credentials.credentials, settings, expected_type="access")
        # A malformed subject is an invalid token (401), never a server error.
        user_id = UUID(str(payload["sub"]))
    except (TokenError, ValueError) as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired token.",
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc
    return UserClaims(user_id=user_id, role=payload.get("role", "student"))


async def get_current_user(
    settings: SettingsDep, credentials: _CredentialsDep = None
) -> UserClaims:
    if credentials is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Not authenticated.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return _claims_from(credentials, settings)


async def get_optional_user(
    settings: SettingsDep, credentials: _CredentialsDep = None
) -> UserClaims | None:
    """No token → anonymous; a *presented* token must still be valid (401 otherwise)."""
    if credentials is None:
        return None
    return _claims_from(credentials, settings)


CurrentUser = Annotated[UserClaims, Depends(get_current_user)]
OptionalUser = Annotated[UserClaims | None, Depends(get_optional_user)]
