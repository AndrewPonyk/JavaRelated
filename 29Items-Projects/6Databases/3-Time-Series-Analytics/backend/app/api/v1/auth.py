"""Auth endpoints: credentials → JWT.

The user store lives in Cassandra (`users` table, migration 004); a bootstrap
admin is created at startup from ADMIN_USERNAME/ADMIN_PASSWORD.
"""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status

from app.api.deps import UserContext, get_current_user
from app.core.config import settings
from app.core.security import create_access_token, hash_password, verify_password
from app.repositories import users as users_repo
from app.schemas.auth import TokenRequest, TokenResponse, UserInfo

router = APIRouter()

# Verified against when the username is unknown, so response timing does not
# reveal which usernames exist.
_DUMMY_HASH = hash_password("timing-equalizer")


@router.post("/token", response_model=TokenResponse)
async def issue_token(payload: TokenRequest) -> TokenResponse:
    user = await users_repo.get(payload.username)
    if user is None:
        verify_password(payload.password, _DUMMY_HASH)
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail="Invalid username or password")
    if not verify_password(payload.password, user.password_hash):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail="Invalid username or password")

    return TokenResponse(
        access_token=create_access_token(user.username, user.roles),
        expires_in=settings.access_token_expire_minutes * 60,
        roles=user.roles,
    )


@router.get("/me", response_model=UserInfo)
async def me(user: Annotated[UserContext, Depends(get_current_user)]) -> UserInfo:
    return UserInfo(subject=user.subject, roles=user.roles)
