"""Pydantic schemas for auth, users, and API keys."""

from __future__ import annotations

import re
from datetime import datetime

from pydantic import BaseModel, Field, field_validator

from app.core.config import settings
from app.core.security import Role

_EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")


class RegisterRequest(BaseModel):
    email: str = Field(max_length=255)
    password: str = Field(min_length=10, max_length=128)
    full_name: str | None = Field(default=None, max_length=255)

    @field_validator("email")
    @classmethod
    def _email_shape(cls, v: str) -> str:
        v = v.strip().lower()
        if not _EMAIL_RE.match(v):
            raise ValueError("must be a valid email address")
        return v


class LoginRequest(BaseModel):
    email: str = Field(max_length=255)
    password: str = Field(min_length=1, max_length=128)

    @field_validator("email")
    @classmethod
    def _normalize(cls, v: str) -> str:
        return v.strip().lower()


class RefreshRequest(BaseModel):
    refresh_token: str = Field(min_length=10, max_length=4096)


class TokenPair(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in: int  # seconds until the access token expires

    @classmethod
    def issue(cls, user_id: int, role: Role) -> TokenPair:
        from app.core.security import create_access_token, create_refresh_token

        return cls(
            access_token=create_access_token(user_id, role),
            refresh_token=create_refresh_token(user_id, role),
            expires_in=settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
        )


class UserRead(BaseModel):
    id: int
    email: str
    full_name: str | None = None
    role: Role
    is_active: bool
    created_at: datetime | None = None

    model_config = {"from_attributes": True}


class UserUpdate(BaseModel):
    """Admin-only mutable fields. At least one must be provided."""

    role: Role | None = None
    is_active: bool | None = None


class ApiKeyCreate(BaseModel):
    name: str = Field(min_length=1, max_length=128)
    role: Role = "scanner"
    expires_days: int | None = Field(default=None, ge=1, le=3650)


class ApiKeyRead(BaseModel):
    id: int
    name: str
    prefix: str
    role: Role
    revoked: bool
    created_at: datetime | None = None
    last_used_at: datetime | None = None
    expires_at: datetime | None = None
    secret: str | None = None  # populated exactly once, at creation

    model_config = {"from_attributes": True}
