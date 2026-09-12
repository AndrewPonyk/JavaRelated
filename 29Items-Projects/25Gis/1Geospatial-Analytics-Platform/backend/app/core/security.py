from dataclasses import dataclass

import jwt
from fastapi import Depends, Header, status
from jwt import InvalidTokenError

from app.core.config import Settings, get_settings
from app.core.errors import AppError


@dataclass(frozen=True)
class Principal:
    subject: str
    roles: set[str]

    def has_role(self, role: str) -> bool:
        return role in self.roles or "admin" in self.roles


def get_current_principal(
    authorization: str | None = Header(default=None),
    settings: Settings = Depends(get_settings),
) -> Principal:
    if not settings.auth_enabled:
        return Principal(subject="development-user", roles={"admin", "analyst"})

    if authorization is None or not authorization.startswith("Bearer "):
        raise AppError("authentication_required", "Bearer token is required", status.HTTP_401_UNAUTHORIZED)

    token = authorization.removeprefix("Bearer ").strip()
    try:
        payload = jwt.decode(
            token,
            settings.jwt_secret,
            algorithms=["HS256"],
            issuer=settings.jwt_issuer,
            audience=settings.jwt_audience,
        )
    except InvalidTokenError as exc:
        raise AppError("invalid_token", "Bearer token is invalid", status.HTTP_401_UNAUTHORIZED) from exc

    raw_roles = payload.get("roles", [])
    roles = {str(role) for role in raw_roles if str(role)}
    return Principal(subject=str(payload["sub"]), roles=roles)


def require_role(role: str):
    def dependency(principal: Principal = Depends(get_current_principal)) -> Principal:
        if not principal.has_role(role):
            raise AppError("forbidden", f"Role '{role}' is required", status.HTTP_403_FORBIDDEN)
        return principal

    return dependency
