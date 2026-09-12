"""Authentication service — Argon2 passwords, signed tokens, TOTP 2FA.

Design (ARCHITECTURE §2.5): demos are anonymous; accounts exist only for
lesson editing and audit access. Tokens are itsdangerous-signed payloads
with a TTL; TOTP is RFC 6238 implemented on stdlib (hmac + base32) and its
secret is stored Fernet-encrypted under a key derived from SECRET_KEY.
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import secrets
import struct
import time

from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerifyMismatchError
from cryptography.fernet import Fernet, InvalidToken
from flask import current_app, request
from itsdangerous import BadSignature, SignatureExpired, URLSafeTimedSerializer

from crypto_toolkit.errors import AuthenticationError, ForbiddenError
from crypto_toolkit.extensions import db
from crypto_toolkit.models import User

_serializer_cache: dict = {}
_fernet_cache: dict = {}

# Switchable for tests
_now = time.time


# ---------------- passwords ----------------


def hash_password(password: str) -> str:
    return PasswordHasher().hash(password)


def verify_password(stored_hash: str, password: str) -> None:
    """Raise VerificationError-style auth error on mismatch."""
    try:
        PasswordHasher().verify(stored_hash, password)
    except VerifyMismatchError as exc:
        raise AuthenticationError("Invalid email or password.") from exc
    except (InvalidHashError, ValueError) as exc:
        raise AuthenticationError("Stored credential is corrupted.") from exc


# ---------------- tokens ----------------


def _serializer():
    key = current_app.config["SECRET_KEY"]
    if key not in _serializer_cache:
        _serializer_cache[key] = URLSafeTimedSerializer(key, salt="auth-token")
    return _serializer_cache[key]


def issue_token(user: User) -> str:
    return _serializer().dumps({"uid": user.id, "admin": bool(user.is_admin)})


def user_from_token(token: str) -> User:
    try:
        payload = _serializer().loads(token, max_age=current_app.config["TOKEN_TTL_HOURS"] * 3600)
    except SignatureExpired as exc:
        raise AuthenticationError("Token expired; log in again.") from exc
    except BadSignature as exc:
        raise AuthenticationError("Invalid token.") from exc
    user = db.session.get(User, payload["uid"])
    if user is None:
        raise AuthenticationError("Account no longer exists.")
    return user


def authenticate_request() -> User | None:
    """Resolve the bearer token to a User, or None for anonymous requests."""
    header = request.headers.get("Authorization", "")
    if not header.startswith("Bearer "):
        return None
    return user_from_token(header.removeprefix("Bearer ").strip())


def require_user() -> User:
    user = authenticate_request()
    if user is None:
        raise AuthenticationError("Authorization bearer token required.")
    return user


def require_admin() -> User:
    user = require_user()
    if not user.is_admin:
        raise ForbiddenError("Admin role required.")
    return user


# ---------------- registration / login ----------------


def register(email: str, password: str) -> User:
    email = email.strip().lower()
    if User.query.filter_by(email=email).first():
        from crypto_toolkit.errors import ConflictError

        raise ConflictError("Email already registered.")
    first_user = User.query.count() == 0  # documented bootstrap: first user = admin
    user = User(email=email, password_hash=hash_password(password), is_admin=first_user)
    db.session.add(user)
    db.session.commit()
    return user


def login(email: str, password: str, totp_code: str | None = None) -> User:
    user = User.query.filter_by(email=email.strip().lower()).first()
    if user is None:
        # burn comparable time so login timing doesn't reveal account existence
        verify_password(PasswordHasher().hash(secrets.token_hex(16)), password)
    else:
        verify_password(user.password_hash, password)
    if user is None:
        raise AuthenticationError("Invalid email or password.")
    if user.totp_secret_encrypted:
        if not totp_code:
            raise AuthenticationError(
                "TOTP code required.",
            )
        if not verify_totp_for_user(user, totp_code):
            raise AuthenticationError("Invalid TOTP code.")
    return user


# ---------------- TOTP (RFC 6238, stdlib only) ----------------


def generate_totp_secret() -> str:
    return base64.b32encode(secrets.token_bytes(20)).decode("ascii").rstrip("=")


def _fernet() -> Fernet:
    key = current_app.config["SECRET_KEY"]
    if key not in _fernet_cache:
        derived = hashlib.sha256(key.encode()).digest()
        _fernet_cache[key] = Fernet(base64.urlsafe_b64encode(derived))
    return _fernet_cache[key]


def encrypt_totp_secret(secret: str) -> str:
    return _fernet().encrypt(secret.encode()).decode()


def decrypt_totp_secret(encrypted: str) -> str:
    try:
        return _fernet().decrypt(encrypted.encode()).decode()
    except InvalidToken as exc:
        raise AuthenticationError("Cannot decrypt TOTP secret (SECRET_KEY changed?).") from exc


def totp_code(secret: str, timestamp: float | None = None) -> str:
    key = base64.b32decode(secret + "=" * (-len(secret) % 8))
    counter = int((_now() if timestamp is None else timestamp) // 30)
    msg = struct.pack(">Q", counter)
    digest = hmac.new(key, msg, hashlib.sha1).digest()
    offset = digest[-1] & 0x0F
    code = (struct.unpack(">I", digest[offset : offset + 4])[0] & 0x7FFFFFFF) % 1_000_000
    return f"{code:06d}"


def _match_totp_timestep(secret: str, code: str) -> int | None:
    """Return the timestep whose code matched (forward drift first), or None."""
    code = code.strip().replace(" ", "")
    if len(code) != 6 or not code.isdigit():
        return None
    now = _now()
    for drift in (0, 1, -1):  # current timestep first, then ±30 s clock drift
        if hmac.compare_digest(totp_code(secret, now + drift * 30), code):
            return int(now // 30) + drift
    return None


def verify_totp(secret: str, code: str) -> bool:
    return _match_totp_timestep(secret, code) is not None


def verify_totp_for_user(user: User, code: str) -> bool:
    """TOTP check with RFC 6238 §5.2 replay protection: a code is accepted only
    if its timestep is NEWER than the last one we accepted for this account.

    Without this, any intercepted code stays valid for its whole 30 s window
    (plus drift), letting an attacker reuse it freely.
    """
    secret = decrypt_totp_secret(user.totp_secret_encrypted)
    matched = _match_totp_timestep(secret, code)
    if matched is None:
        return False
    if user.totp_last_timestep is not None and matched <= user.totp_last_timestep:
        return False
    user.totp_last_timestep = matched
    db.session.commit()  # advance the high-water mark immediately
    return True


def otpauth_uri(secret: str, email: str) -> str:
    return f"otpauth://totp/CryptographyToolkit:{email}?secret={secret}&issuer=CryptographyToolkit"
