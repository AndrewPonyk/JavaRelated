"""Reusable auth dependencies.

Two credential planes (docs/ARCHITECTURE.md §2.5):
- devices ingest with X-API-Key (can ONLY ingest),
- dashboard users query with JWT bearer (can NEVER ingest).

Ingest accepts two key shapes:
- the shared gateway key (DEVICE_API_KEY env) — multi-device batches;
- a per-device key "<device_id>.<secret>" minted at registration — that
  device's data only (sha256(secret) checked against devices.api_key_hash).
"""

from __future__ import annotations

import hmac
from typing import Annotated, Literal

from fastapi import Depends, Header, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel

from app.core.config import settings
from app.core.security import decode_access_token, split_device_key
from app.services import device_registry

_bearer = HTTPBearer(auto_error=False)


class UserContext(BaseModel):
    subject: str
    roles: list[str]


class IngestPrincipal(BaseModel):
    kind: Literal["gateway", "device"]
    device_id: str | None = None


async def require_ingest_principal(
    x_api_key: Annotated[str | None, Header(alias="X-API-Key")] = None,
) -> IngestPrincipal:
    if x_api_key is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail="Missing API key")

    if settings.device_api_key and hmac.compare_digest(x_api_key, settings.device_api_key):
        return IngestPrincipal(kind="gateway")

    parsed = split_device_key(x_api_key)
    if parsed is not None:
        device_id, secret = parsed
        if await device_registry.verify_device_secret(device_id, secret):
            return IngestPrincipal(kind="device", device_id=device_id)

    raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail="Invalid API key")


async def get_current_user(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer)] = None,
) -> UserContext:
    if credentials is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail="Not authenticated")
    import jwt  # lazy, matching core.security

    try:
        payload = decode_access_token(credentials.credentials)
    except jwt.InvalidTokenError as exc:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail="Invalid token") from exc
    return UserContext(subject=payload.get("sub", ""), roles=payload.get("roles", []))


async def require_operator(
    user: Annotated[UserContext, Depends(get_current_user)],
) -> UserContext:
    """Gate for mutating endpoints (device management)."""
    if not {"operator", "admin"} & set(user.roles):
        raise HTTPException(status.HTTP_403_FORBIDDEN, detail="Operator role required")
    return user
