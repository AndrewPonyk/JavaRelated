"""Auth endpoints — register/login, TOTP enrolment, whoami, audit (admin)."""

from __future__ import annotations

from flask import Blueprint, jsonify, request
from marshmallow import Schema, ValidationError, fields, validate

from crypto_toolkit.errors import InvalidInputError
from crypto_toolkit.extensions import limiter
from crypto_toolkit.services import audit_service, auth_service

bp = Blueprint("auth", __name__)


def _parse(schema: Schema):
    body = request.get_json(silent=True)
    if body is None:
        raise InvalidInputError("Request body must be JSON.")
    try:
        return schema.load(body)
    except ValidationError as err:
        raise InvalidInputError(f"Schema validation failed: {err.messages}") from err


class RegisterSchema(Schema):
    email = fields.Email(required=True)
    password = fields.Str(required=True, validate=validate.Length(min=8, max=128))


class LoginSchema(Schema):
    email = fields.Email(required=True)
    password = fields.Str(required=True, validate=validate.Length(min=1, max=128))
    totp_code = fields.Str(
        load_default=None,
        allow_none=True,
        validate=validate.Regexp(r"^\d{6}$", error="TOTP code must be 6 digits."),
    )


class TotpEnableSchema(Schema):
    code = fields.Str(required=True, validate=validate.Regexp(r"^\d{6}$"))
    secret_base32 = fields.Str(required=True, validate=validate.Length(min=16, max=64))


class TotpDisableSchema(Schema):
    code = fields.Str(required=True, validate=validate.Regexp(r"^\d{6}$"))
    password = fields.Str(required=True, validate=validate.Length(min=1, max=128))


def _auth_response(user) -> dict:
    return {"token": auth_service.issue_token(user), "user": user.to_dict()}


@bp.post("/register")
@limiter.limit("3 per minute")
def register():
    args = _parse(RegisterSchema())
    user = auth_service.register(args["email"], args["password"])
    audit_service.record_audit("auth.register", {"user_id": user.id}, user=user)
    return jsonify(data=_auth_response(user)), 201


@bp.post("/login")
@limiter.limit("5 per minute")
def login():
    args = _parse(LoginSchema())
    user = auth_service.login(args["email"], args["password"], args["totp_code"])
    audit_service.record_audit("auth.login", {"user_id": user.id}, user=user)
    return jsonify(data=_auth_response(user))


@bp.get("/whoami")
def whoami():
    user = auth_service.require_user()
    return jsonify(data=user.to_dict())


@bp.post("/totp/setup")
@limiter.limit("5 per minute")
def totp_setup():
    """Generate a fresh TOTP secret (not yet active — confirm with a code)."""
    user = auth_service.require_user()
    secret = auth_service.generate_totp_secret()
    return jsonify(
        data={
            "secret_base32": secret,
            "otpauth_uri": auth_service.otpauth_uri(secret, user.email),
            "next": "POST /api/auth/totp/enable {code} from your authenticator app.",
        }
    )


@bp.post("/totp/enable")
@limiter.limit("5 per minute")
def totp_enable():
    user = auth_service.require_user()
    args = _parse(TotpEnableSchema())
    secret = args["secret_base32"]
    if not auth_service.verify_totp(secret, args["code"]):
        raise InvalidInputError("Code does not match the secret — check the clock and retry.")
    user.totp_secret_encrypted = auth_service.encrypt_totp_secret(secret)
    user.totp_last_timestep = None  # fresh enrolment: replay watermark resets
    from crypto_toolkit.extensions import db

    db.session.commit()
    audit_service.record_audit("auth.totp.enabled", {"user_id": user.id}, user=user)
    return jsonify(data={"totp_enabled": True})


@bp.post("/totp/disable")
@limiter.limit("5 per minute")
def totp_disable():
    user = auth_service.require_user()
    args = _parse(TotpDisableSchema())
    if not user.totp_secret_encrypted:
        raise InvalidInputError("TOTP is not enabled.")
    auth_service.verify_password(user.password_hash, args["password"])
    secret = auth_service.decrypt_totp_secret(user.totp_secret_encrypted)
    if not auth_service.verify_totp(secret, args["code"]):
        raise InvalidInputError("Invalid TOTP code.")
    user.totp_secret_encrypted = None
    user.totp_last_timestep = None
    from crypto_toolkit.extensions import db

    db.session.commit()
    audit_service.record_audit("auth.totp.disabled", {"user_id": user.id}, user=user)
    return jsonify(data={"totp_enabled": False})


@bp.get("/audit")
@limiter.limit("30 per minute")
def audit():
    """Admin-only audit listing (metadata only — see audit_service policy)."""
    auth_service.require_admin()
    limit = request.args.get("limit", 100, type=int)
    offset = request.args.get("offset", 0, type=int)
    return jsonify(data=audit_service.list_audit(limit=limit, offset=offset))
