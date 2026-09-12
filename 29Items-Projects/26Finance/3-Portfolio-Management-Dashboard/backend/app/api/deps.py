"""Shared FastAPI dependencies: DB session, settings, and the current user."""

from __future__ import annotations

from fastapi import Depends
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings
from app.core.exceptions import AuthError
from app.core.security import decode_access_token
from app.db.session import get_db
from app.models.user import User

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/v1/auth/login")


def get_settings_dep() -> Settings:
    return get_settings()


def get_current_user(token: str = Depends(oauth2_scheme), db: Session = Depends(get_db)) -> User:
    """Resolve and validate the bearer token into an active ``User``."""
    user_id = decode_access_token(token)
    if user_id is None:
        raise AuthError("Invalid or expired authentication token")
    user = db.get(User, int(user_id))
    if user is None or not user.is_active:
        raise AuthError("User not found or inactive")
    return user


# Re-exported so endpoints can `Depends(get_db)` from one place.
__all__ = ["get_db", "get_current_user", "get_settings_dep", "oauth2_scheme"]
