"""Authentication primitives.

Phase 1 uses a simple API-key scheme enforced as a FastAPI dependency. The comparison
is constant-time to avoid timing side-channels. Phase 2 adds JWT/OIDC with scopes —
the dependency signature stays the same so callers don't change.
"""

from __future__ import annotations

import secrets

from fastapi import Security
from fastapi.security import APIKeyHeader

from app.core.config import Settings, get_settings
from app.core.exceptions import AuthError

# auto_error=False: we raise our own AuthError so the response uses the shared envelope.
_api_key_header = APIKeyHeader(name="X-API-Key", auto_error=False)


async def require_api_key(
    api_key: str | None = Security(_api_key_header),
) -> str:
    """FastAPI dependency: validate the caller's API key.

    TODO(phase-2): support multiple keys + scopes, and JWT bearer tokens.
    Resolve keys from AWS Secrets Manager in non-dev environments.
    """
    settings: Settings = get_settings()
    if not api_key or not secrets.compare_digest(api_key, settings.api_key):
        raise AuthError("Missing or invalid API key.")
    return api_key
