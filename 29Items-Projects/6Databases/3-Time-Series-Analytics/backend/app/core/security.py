"""AuthN/AuthZ primitives: JWT + passwords for dashboard users, API keys for devices.

Model (docs/ARCHITECTURE.md §2.5):
- device keys can only ingest; user tokens can only query/manage;
- device key format is "<device_id>.<secret>" — only sha256(secret) is stored;
- user passwords are PBKDF2-SHA256, never plaintext.
"""

from __future__ import annotations

import hashlib
import hmac
import secrets
from datetime import UTC, datetime, timedelta
from typing import Any

from app.core.config import settings

# --- user passwords ------------------------------------------------------------

PBKDF2_ITERATIONS = 120_000


def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, PBKDF2_ITERATIONS)
    return f"pbkdf2_sha256${PBKDF2_ITERATIONS}${salt.hex()}${digest.hex()}"


def verify_password(password: str, stored: str) -> bool:
    try:
        algorithm, iterations, salt_hex, digest_hex = stored.split("$")
        if algorithm != "pbkdf2_sha256":
            return False
        digest = hashlib.pbkdf2_hmac(
            "sha256", password.encode(), bytes.fromhex(salt_hex), int(iterations)
        )
        return hmac.compare_digest(digest.hex(), digest_hex)
    except (ValueError, TypeError):
        return False


# --- device API keys ------------------------------------------------------------


def generate_device_secret() -> str:
    return secrets.token_hex(24)


def compose_device_key(device_id: str, secret: str) -> str:
    """The full credential handed to a device at registration."""
    return f"{device_id}.{secret}"


def split_device_key(api_key: str) -> tuple[str, str] | None:
    """'<device_id>.<secret>' → (device_id, secret); None if not that shape."""
    device_id, sep, secret = api_key.partition(".")
    if not sep or not device_id or not secret:
        return None
    return device_id, secret


def hash_api_key(secret: str) -> str:
    # Secrets are high-entropy random strings, so an unsalted fast hash is
    # appropriate here (unlike user-chosen passwords, which use PBKDF2 above).
    return hashlib.sha256(secret.encode()).hexdigest()


def verify_api_key(presented_secret: str, stored_hash: str) -> bool:
    return hmac.compare_digest(hash_api_key(presented_secret), stored_hash)


# --- user JWTs ----------------------------------------------------------------


def create_access_token(subject: str, roles: list[str] | None = None) -> str:
    import jwt  # lazy: PyJWT only needed on auth paths

    now = datetime.now(UTC)
    payload: dict[str, Any] = {
        "sub": subject,
        "roles": roles or ["viewer"],
        "iat": now,
        "exp": now + timedelta(minutes=settings.access_token_expire_minutes),
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)


def decode_access_token(token: str) -> dict[str, Any]:
    """Return the token payload or raise jwt.InvalidTokenError."""
    import jwt

    return jwt.decode(token, settings.jwt_secret, algorithms=[settings.jwt_algorithm])
