"""Authentication business logic: registration, login, refresh rotation.

Domain errors are raised as typed exceptions; the auth endpoints map them to
HTTP statuses (409/401). Passwords never leave this module unhashed.
"""

from __future__ import annotations

from datetime import UTC, datetime

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings
from app.core.security import (
    TokenError,
    create_access_token,
    create_refresh_token,
    decode_token,
    hash_password,
    verify_password,
)
from app.db.models import RefreshToken, User
from app.schemas.auth import TokenPair


class AuthError(Exception):
    """Base class for auth failures; message is user-safe."""


class EmailAlreadyRegisteredError(AuthError):
    pass


class InvalidCredentialsError(AuthError):
    pass


class InvalidRefreshTokenError(AuthError):
    pass


# Verified against when the email is unknown, so login latency does not reveal
# whether an account exists (anti-enumeration timing equalizer).
_DUMMY_HASH = hash_password("timing-equalizer-not-a-real-password")


async def register_user(session: AsyncSession, *, email: str, password: str) -> User:
    normalized = email.strip().lower()
    existing = await session.execute(select(User.id).where(User.email == normalized))
    if existing.scalar_one_or_none() is not None:
        raise EmailAlreadyRegisteredError("An account with this email already exists.")
    user = User(email=normalized, password_hash=hash_password(password), role="student")
    session.add(user)
    try:
        await session.commit()
    except IntegrityError as exc:
        # Two concurrent registrations raced past the pre-check; the unique
        # index is the source of truth — same 409 as the sequential case.
        await session.rollback()
        raise EmailAlreadyRegisteredError("An account with this email already exists.") from exc
    await session.refresh(user)
    return user


async def login(
    session: AsyncSession, *, email: str, password: str, settings: Settings
) -> TokenPair:
    normalized = email.strip().lower()
    result = await session.execute(select(User).where(User.email == normalized))
    user = result.scalar_one_or_none()
    stored_hash = user.password_hash if user is not None else _DUMMY_HASH
    if not verify_password(password, stored_hash) or user is None:
        raise InvalidCredentialsError("Incorrect email or password.")
    return await _issue_pair(session, user, settings)


async def refresh(session: AsyncSession, *, refresh_token: str, settings: Settings) -> TokenPair:
    """Rotate a refresh token: the presented token is revoked, a new pair issued."""
    try:
        payload = decode_token(refresh_token, settings, expected_type="refresh")
    except TokenError as exc:
        raise InvalidRefreshTokenError(str(exc)) from exc

    record = await session.get(RefreshToken, payload["jti"])
    if record is None or record.revoked or _expired(record.expires_at):
        raise InvalidRefreshTokenError("Refresh token is no longer valid.")

    user = await session.get(User, record.user_id)
    if user is None:
        raise InvalidRefreshTokenError("Account no longer exists.")

    record.revoked = True  # rotation: each refresh token is single-use
    pair = await _issue_pair(session, user, settings)
    return pair


async def logout(session: AsyncSession, *, refresh_token: str, settings: Settings) -> None:
    """Revoke a refresh token. Idempotent; invalid tokens are a no-op
    (logout must never fail the user)."""
    try:
        payload = decode_token(refresh_token, settings, expected_type="refresh")
    except TokenError:
        return
    record = await session.get(RefreshToken, payload["jti"])
    if record is not None and not record.revoked:
        record.revoked = True
        await session.commit()


async def _issue_pair(session: AsyncSession, user: User, settings: Settings) -> TokenPair:
    access = create_access_token(subject=str(user.id), role=user.role, settings=settings)
    issued = create_refresh_token(subject=str(user.id), settings=settings)
    session.add(RefreshToken(jti=issued.jti, user_id=user.id, expires_at=issued.expires_at))
    await session.commit()
    return TokenPair(
        access_token=access,
        refresh_token=issued.token,
        expires_in=settings.access_token_ttl_seconds,
    )


def _expired(expires_at: datetime) -> bool:
    # SQLite returns naive datetimes for tz-aware columns; treat naive as UTC.
    if expires_at.tzinfo is None:
        expires_at = expires_at.replace(tzinfo=UTC)
    return expires_at <= datetime.now(UTC)
