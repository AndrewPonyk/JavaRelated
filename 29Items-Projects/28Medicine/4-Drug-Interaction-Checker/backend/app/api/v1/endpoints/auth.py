"""Development token endpoint.

Issues short-lived JWTs for local testing and integration tests. Disabled in
production (where real OIDC tokens are expected).
"""

from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel, Field

from app.core.config import get_settings
from app.core.security import create_access_token

router = APIRouter(prefix="/auth", tags=["auth"])


class TokenRequest(BaseModel):
    subject: str = Field(default="dev-user")
    scopes: list[str] = Field(default_factory=lambda: ["pharmacy:check", "admin"])


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    scope: str


@router.post("/token", response_model=TokenResponse)
async def issue_dev_token(request: TokenRequest) -> TokenResponse:
    settings = get_settings()
    if settings.is_production or not settings.dev_token_endpoint_enabled:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Not available")
    scope = " ".join(request.scopes)
    token = create_access_token(request.subject, {"scope": scope})
    return TokenResponse(access_token=token, scope=scope)
