"""Authentication endpoint: issue a short-lived JWT for the console.

This is a demo password login. In production, replace with a real identity provider
(OIDC / Google Identity) and map IdP groups to scopes.
"""

from __future__ import annotations

import hmac

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel

from app.api.deps import settings_dep
from app.core.config import Settings
from app.core.security import create_access_token

router = APIRouter(prefix="/auth", tags=["auth"])


class LoginRequest(BaseModel):
    username: str
    password: str


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    expires_in: int
    scopes: list[str]


@router.post("/token", response_model=TokenResponse)
def login(payload: LoginRequest, settings: Settings = Depends(settings_dep)) -> TokenResponse:
    valid = hmac.compare_digest(payload.username, settings.console_username) and (
        hmac.compare_digest(payload.password, settings.console_password)
    )
    if not valid:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid credentials")

    scopes = ["classify", "taxonomy:read", "taxonomy:write", "admin"]
    token = create_access_token(
        subject=payload.username,
        scopes=scopes,
        settings=settings,
        expires_in=settings.jwt_expires_seconds,
    )
    return TokenResponse(access_token=token, expires_in=settings.jwt_expires_seconds, scopes=scopes)
