"""Auth request/response models."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class TokenRequest(BaseModel):
    username: str = Field(min_length=1, max_length=64)
    password: str = Field(min_length=1, max_length=128)


class TokenResponse(BaseModel):
    access_token: str
    token_type: Literal["bearer"] = "bearer"
    expires_in: int  # seconds
    roles: list[str]


class UserInfo(BaseModel):
    subject: str
    roles: list[str]
