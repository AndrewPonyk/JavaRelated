"""Authentication endpoints: register, login, refresh (rotating), logout, me."""

from fastapi import APIRouter, HTTPException, status

from app.api.deps import CurrentUser, SessionDep, SettingsDep
from app.db.models import User
from app.schemas.auth import (
    LoginRequest,
    LogoutRequest,
    RefreshRequest,
    RegisterRequest,
    TokenPair,
    UserRead,
)
from app.services import auth_service
from app.services.auth_service import (
    EmailAlreadyRegisteredError,
    InvalidCredentialsError,
    InvalidRefreshTokenError,
)

router = APIRouter()


@router.post("/register", response_model=UserRead, status_code=status.HTTP_201_CREATED)
async def register(payload: RegisterRequest, session: SessionDep) -> UserRead:
    try:
        user = await auth_service.register_user(
            session, email=payload.email, password=payload.password
        )
    except EmailAlreadyRegisteredError as exc:
        raise HTTPException(status.HTTP_409_CONFLICT, detail=str(exc)) from exc
    return UserRead.model_validate(user)


@router.post("/login", response_model=TokenPair)
async def login(payload: LoginRequest, session: SessionDep, settings: SettingsDep) -> TokenPair:
    try:
        return await auth_service.login(
            session, email=payload.email, password=payload.password, settings=settings
        )
    except InvalidCredentialsError as exc:
        raise HTTPException(
            status.HTTP_401_UNAUTHORIZED,
            detail=str(exc),
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc


@router.post("/refresh", response_model=TokenPair)
async def refresh(payload: RefreshRequest, session: SessionDep, settings: SettingsDep) -> TokenPair:
    try:
        return await auth_service.refresh(
            session, refresh_token=payload.refresh_token, settings=settings
        )
    except InvalidRefreshTokenError as exc:
        raise HTTPException(
            status.HTTP_401_UNAUTHORIZED,
            detail=str(exc),
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
async def logout(payload: LogoutRequest, session: SessionDep, settings: SettingsDep) -> None:
    await auth_service.logout(session, refresh_token=payload.refresh_token, settings=settings)


@router.get("/me", response_model=UserRead)
async def me(user: CurrentUser, session: SessionDep) -> UserRead:
    record = await session.get(User, user.user_id)
    if record is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail="Account no longer exists.")
    return UserRead.model_validate(record)
