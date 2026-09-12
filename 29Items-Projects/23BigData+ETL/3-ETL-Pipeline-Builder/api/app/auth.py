"""Pluggable request authentication (ARCHITECTURE §2.5).

Modes (AUTH_MODE):
    none     — open; local compose default
    api_key  — X-API-Key header (or api_key query param for WebSockets)
    jwt      — Bearer token (or token query param for WebSockets):
               HS256 with JWT_SECRET, or RS256 against JWT_JWKS_URL — the JWKS
               path works with any OIDC IdP (Cognito, Entra ID, Keycloak).

One dependency covers both HTTP routes and WebSockets via HTTPConnection;
failures raise 401 for HTTP and close code 1008 for WebSockets. Misconfigured
auth fails CLOSED.
"""

from __future__ import annotations

import hmac
import logging
import threading
from typing import Any

from fastapi import HTTPException, WebSocketException, status
from starlette.requests import HTTPConnection

from app.config import Settings, get_settings

log = logging.getLogger(__name__)

_jwks_clients: dict[str, Any] = {}  # url → jwt.PyJWKClient (lazy import)
_jwks_lock = threading.Lock()


def _deny(conn: HTTPConnection, reason: str) -> Exception:
    if conn.scope["type"] == "websocket":
        return WebSocketException(code=status.WS_1008_POLICY_VIOLATION, reason=reason)
    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail=reason,
        headers={"WWW-Authenticate": "Bearer"},
    )


def _extract_token(conn: HTTPConnection) -> str:
    authorization = conn.headers.get("authorization", "")
    if authorization.lower().startswith("bearer "):
        return authorization[7:].strip()
    return conn.query_params.get("token", "")


def decode_token(token: str, settings: Settings) -> dict:
    """Verify signature + registered claims; returns the payload."""
    import jwt as pyjwt  # lazy: PyJWT is an api-only dependency

    kwargs: dict = {"options": {"verify_aud": bool(settings.jwt_audience)}}
    if settings.jwt_audience:
        kwargs["audience"] = settings.jwt_audience
    if settings.jwt_issuer:
        kwargs["issuer"] = settings.jwt_issuer

    if settings.jwt_secret:
        return pyjwt.decode(token, settings.jwt_secret, algorithms=["HS256"], **kwargs)

    if settings.jwt_jwks_url:
        with _jwks_lock:
            client = _jwks_clients.get(settings.jwt_jwks_url)
            if client is None:
                # PyJWKClient caches keys internally; the fetch happens once
                # per key id, so the sync call inside async handlers is rare.
                client = pyjwt.PyJWKClient(settings.jwt_jwks_url, cache_keys=True)
                _jwks_clients[settings.jwt_jwks_url] = client
        signing_key = client.get_signing_key_from_jwt(token)
        return pyjwt.decode(token, signing_key.key, algorithms=["RS256"], **kwargs)

    raise RuntimeError("AUTH_MODE=jwt requires JWT_SECRET or JWT_JWKS_URL")


async def require_auth(conn: HTTPConnection) -> None:
    settings = get_settings()
    mode = settings.auth_mode

    if mode == "none":
        return

    if mode == "api_key":
        supplied = conn.headers.get("x-api-key") or conn.query_params.get("api_key") or ""
        if settings.api_key and hmac.compare_digest(supplied, settings.api_key):
            return
        raise _deny(conn, "invalid or missing API key")

    if mode == "jwt":
        token = _extract_token(conn)
        if not token:
            raise _deny(conn, "missing bearer token")
        try:
            decode_token(token, settings)
        except Exception as exc:
            log.info("rejected token: %s", exc)
            raise _deny(conn, "invalid token") from exc
        return

    raise _deny(conn, f"unsupported auth mode: {mode}")
