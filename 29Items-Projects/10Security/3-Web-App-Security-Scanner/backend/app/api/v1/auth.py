"""Authentication endpoints: register/login/refresh/me + API key lifecycle."""

from __future__ import annotations

from datetime import UTC, datetime, timedelta

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.metrics import metrics
from app.core.ratelimit import auth_rate_limit
from app.core.security import (
    Principal,
    TokenError,
    decode_token,
    generate_api_key,
    get_current_principal,
    hash_password,
    role_rank,
    verify_password,
)
from app.db.session import get_session
from app.models.api_key import ApiKey
from app.models.user import User
from app.schemas.auth import (
    ApiKeyCreate,
    ApiKeyRead,
    LoginRequest,
    RefreshRequest,
    RegisterRequest,
    TokenPair,
    UserRead,
)

router = APIRouter()


async def _require_user_row(principal: Principal, session: AsyncSession) -> User:
    """Resolve a JWT principal to its user row. API keys have no profile."""
    if principal.auth_type != "jwt" or not principal.id.isdigit():
        raise HTTPException(status.HTTP_404_NOT_FOUND, "No user profile for API-key credentials")
    user = await session.get(User, int(principal.id))
    if user is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Account no longer exists")
    return user


# ── Interactive auth ────────────────────────────────────────────────────


@router.post(
    "/register",
    response_model=UserRead,
    status_code=status.HTTP_201_CREATED,
    summary="Register a new user",
)
async def register(
    payload: RegisterRequest,
    session: AsyncSession = Depends(get_session),
    _rate: None = Depends(auth_rate_limit),
) -> User:
    """Open registration — always creates a 'viewer'; admins promote via PATCH /users."""
    existing = await session.scalar(select(User.id).where(User.email == payload.email))
    if existing:
        raise HTTPException(status.HTTP_409_CONFLICT, "Email already registered")
    user = User(
        email=payload.email,
        full_name=payload.full_name,
        hashed_password=hash_password(payload.password),
        role="viewer",
    )
    session.add(user)
    await session.commit()
    await session.refresh(user)
    metrics.count("users_registered_total")
    return user


@router.post("/login", response_model=TokenPair, summary="Login")
async def login(
    payload: LoginRequest,
    session: AsyncSession = Depends(get_session),
    _rate: None = Depends(auth_rate_limit),
) -> TokenPair:
    """Password login → access+refresh token pair. Generic failure message."""
    user = await session.scalar(select(User).where(User.email == payload.email))
    if user is None or not verify_password(payload.password, user.hashed_password):
        metrics.count("auth_login_total", labels={"outcome": "failed"})
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid email or password")
    if not user.is_active:
        metrics.count("auth_login_total", labels={"outcome": "disabled"})
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Account is disabled")
    metrics.count("auth_login_total", labels={"outcome": "ok"})
    return TokenPair.issue(user.id, user.role)


@router.post("/refresh", response_model=TokenPair, summary="Refresh access token")
async def refresh(
    payload: RefreshRequest,
    session: AsyncSession = Depends(get_session),
) -> TokenPair:
    """Exchange a valid refresh token for a new pair.

    Role is re-read from the DB so promotions/demotions take effect on refresh.
    """
    try:
        claims = decode_token(payload.refresh_token, expected_type="refresh")
    except TokenError as exc:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, str(exc)) from exc
    user = await session.get(User, int(claims["sub"]))
    if user is None or not user.is_active:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Account no longer active")
    return TokenPair.issue(user.id, user.role)


@router.get("/me", response_model=UserRead, summary="Current user profile")
async def me(
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(get_current_principal),
) -> User:
    return await _require_user_row(principal, session)


# ── API keys (CI service accounts) ─────────────────────────────────────


@router.post(
    "/api-keys",
    response_model=ApiKeyRead,
    status_code=status.HTTP_201_CREATED,
    summary="Issue an API key",
)
async def create_api_key(
    payload: ApiKeyCreate,
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(get_current_principal),
) -> ApiKeyRead:
    """Issue a service-account key. The full secret is shown exactly once."""
    if role_rank(principal.role) < role_rank("scanner"):
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Requires role 'scanner'")
    if role_rank(payload.role) > role_rank(principal.role):
        raise HTTPException(
            status.HTTP_403_FORBIDDEN,
            f"Cannot issue a key with role above your own ('{payload.role}')",
        )
    user = await _require_user_row(principal, session)
    secret, prefix, secret_hash = generate_api_key()
    api_key = ApiKey(
        name=payload.name,
        prefix=prefix,
        secret_hash=secret_hash,
        role=payload.role,
        created_by=user.id,
        expires_at=(
            datetime.now(UTC) + timedelta(days=payload.expires_days)
            if payload.expires_days
            else None
        ),
    )
    session.add(api_key)
    await session.commit()
    await session.refresh(api_key)
    metrics.count("api_keys_issued_total")
    return ApiKeyRead.model_validate(api_key).model_copy(update={"secret": secret})


@router.get("/api-keys", response_model=list[ApiKeyRead], summary="List API keys")
async def list_api_keys(
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(get_current_principal),
) -> list[ApiKey]:
    """Admins see every key; scanners/viewers see their own."""
    stmt = select(ApiKey).order_by(ApiKey.created_at.desc())
    if principal.role != "admin":
        if principal.auth_type != "jwt" or not principal.id.isdigit():
            return []
        stmt = stmt.where(ApiKey.created_by == int(principal.id))
    return list((await session.scalars(stmt)).all())


@router.delete(
    "/api-keys/{key_id}", status_code=status.HTTP_204_NO_CONTENT, summary="Revoke an API key"
)
async def revoke_api_key(
    key_id: int,
    session: AsyncSession = Depends(get_session),
    principal: Principal = Depends(get_current_principal),
) -> None:
    """Revoke a key — owners revoke their own; admins revoke any."""
    api_key = await session.get(ApiKey, key_id)
    if api_key is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, f"API key {key_id} not found")
    if principal.role != "admin":
        owner_ok = (
            principal.auth_type == "jwt"
            and principal.id.isdigit()
            and (api_key.created_by == int(principal.id))
        )
        if not owner_ok:
            raise HTTPException(status.HTTP_403_FORBIDDEN, "Not your API key")
    api_key.revoked = True
    await session.commit()
    metrics.count("api_keys_revoked_total")
