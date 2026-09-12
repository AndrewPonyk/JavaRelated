"""Attack gallery endpoints — every demo runs server-side and returns
its result paired with the countermeasure that defeats it."""

from __future__ import annotations

from flask import Blueprint, jsonify
from marshmallow import Schema, fields, validate

from crypto_toolkit.api.routes_crypto import _parse_body
from crypto_toolkit.extensions import limiter
from crypto_toolkit.services import attacks, audit_service

bp = Blueprint("attacks", __name__)


class LengthExtensionSchema(Schema):
    message = fields.Str(load_default="amount=100&to=alice", validate=validate.Length(max=256))
    append = fields.Str(load_default="&admin=true", validate=validate.Length(max=128))


class GcmReuseSchema(Schema):
    known_plaintext = fields.Str(load_default="transfer: 100 USD to Bob", validate=validate.Length(max=128))
    secret_plaintext = fields.Str(load_default="transfer: 900 USD to Eve", validate=validate.Length(max=128))


@bp.post("/ecb-penguin")
@limiter.limit("10 per minute")
def ecb_penguin():
    result = attacks.ecb_penguin()
    audit_service.record_audit("attack.ecb-penguin", {})
    return jsonify(data=result)


@bp.post("/length-extension")
@limiter.limit("10 per minute")
def length_extension():
    args = _parse_body(LengthExtensionSchema())
    result = attacks.length_extension(args["message"], args["append"])
    audit_service.record_audit("attack.length-extension", {})
    return jsonify(data=result)


@bp.post("/gcm-nonce-reuse")
@limiter.limit("10 per minute")
def gcm_nonce_reuse():
    args = _parse_body(GcmReuseSchema())
    result = attacks.gcm_nonce_reuse(args["known_plaintext"], args["secret_plaintext"])
    audit_service.record_audit("attack.gcm-nonce-reuse", {})
    return jsonify(data=result)
