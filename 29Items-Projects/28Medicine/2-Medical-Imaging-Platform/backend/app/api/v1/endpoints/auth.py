"""Authentication + user management (OAuth2 password grant → JWT).

Local/dev auth backed by the users table. In staging/prod this federates to
Cognito / hospital SSO (OIDC); the token contract stays identical.
"""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, status
from fastapi.security import OAuth2PasswordRequestForm

from app.api.deps import CurrentUser, DbSession, require_scope
from app.core.config import settings
from app.core.exceptions import ConflictError, UnauthorizedError
from app.core.security import Role, Scope, create_access_token
from app.schemas.user import Token, TokenPayload, UserCreate, UserRead
from app.services.user_service import UserService

router = APIRouter()


@router.post("/token", response_model=Token, summary="Exchange credentials for a JWT")
async def login(
    form: Annotated[OAuth2PasswordRequestForm, Depends()],
    session: DbSession,
) -> Token:
    user = await UserService(session).authenticate(form.username, form.password)
    if user is None:
        raise UnauthorizedError("Incorrect email or password")
    token = create_access_token(subject=user.email, role=Role(user.role))
    return Token(
        access_token=token,
        token_type="bearer",
        expires_in=settings.access_token_expire_minutes * 60,
    )


@router.post(
    "/register",
    response_model=UserRead,
    status_code=status.HTTP_201_CREATED,
    summary="Create a user (admin only)",
)
async def register(
    body: UserCreate,
    session: DbSession,
    _: Annotated[CurrentUser, Depends(require_scope(Scope.ADMIN))],
) -> UserRead:
    svc = UserService(session)
    if await svc.get_by_email(body.email):
        raise ConflictError("A user with this email already exists")
    user = await svc.create(
        email=body.email,
        password=body.password,
        full_name=body.full_name,
        role=Role(body.role),
    )
    return UserRead.model_validate(user)


@router.get("/me", response_model=TokenPayload, summary="Current principal")
async def me(user: CurrentUser) -> TokenPayload:
    return user
