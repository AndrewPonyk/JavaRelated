"""Crypto demo endpoints: AES, SHA-3, Argon2, RSA, ECDSA.

Pattern: marshmallow schema validates the request body, the service does
the work, the route just shapes the response. Rate limits sit *before* any
crypto runs (CPU-bound protection, TECH-NOTES §3.6 #9), and every operation
records a metadata-only audit row.
"""

from __future__ import annotations

import base64
import binascii

from flask import Blueprint, jsonify, request
from marshmallow import Schema, ValidationError, fields, validate

from crypto_toolkit.errors import InvalidInputError
from crypto_toolkit.extensions import limiter
from crypto_toolkit.services import (
    aes_service,
    argon2_service,
    audit_service,
    ecdsa_service,
    rsa_service,
    sha3_service,
)

bp = Blueprint("crypto", __name__)


# ---------- schemas ----------


class AesEncryptSchema(Schema):
    plaintext = fields.Str(required=True, validate=validate.Length(min=1, max=8192))
    key_b64 = fields.Str(required=True)
    mode = fields.Str(load_default="gcm", validate=validate.OneOf(["gcm", "cbc", "ecb"]))
    demo = fields.Bool(load_default=False)


class AesDecryptSchema(Schema):
    ciphertext_b64 = fields.Str(required=True)
    iv_b64 = fields.Str(load_default="")
    tag_b64 = fields.Str(load_default="")
    key_b64 = fields.Str(required=True)
    mode = fields.Str(load_default="gcm", validate=validate.OneOf(["gcm", "cbc", "ecb"]))


class Sha3Schema(Schema):
    message = fields.Str(required=True, validate=validate.Length(min=1, max=8192))
    algorithm = fields.Str(
        load_default="sha3-256",
        validate=validate.OneOf(["sha3-256", "sha3-384", "sha3-512"]),
    )


class Argon2HashSchema(Schema):
    password = fields.Str(required=True, validate=validate.Length(min=1, max=1024))
    preset = fields.Str(
        load_default="interactive",
        validate=validate.OneOf(["interactive", "moderate", "paranoid"]),
    )


class Argon2VerifySchema(Schema):
    hash_string = fields.Str(required=True)
    password = fields.Str(required=True, validate=validate.Length(min=1, max=1024))


class RsaKeygenSchema(Schema):
    bits = fields.Int(load_default=2048, validate=validate.OneOf([2048, 3072, 4096]))


class RsaEncryptSchema(Schema):
    public_pem = fields.Str(required=True)
    plaintext = fields.Str(required=True, validate=validate.Length(min=1, max=8192))


class RsaDecryptSchema(Schema):
    private_pem = fields.Str(required=True)
    ciphertext_b64 = fields.Str(required=True)


class RsaSignSchema(Schema):
    private_pem = fields.Str(required=True)
    message = fields.Str(required=True, validate=validate.Length(min=1, max=8192))


class RsaVerifySchema(Schema):
    public_pem = fields.Str(required=True)
    message = fields.Str(required=True, validate=validate.Length(min=1, max=8192))
    signature_b64 = fields.Str(required=True)


class RsaMalleabilitySchema(Schema):
    public_pem = fields.Str(required=True)
    private_pem = fields.Str(required=True)
    message = fields.Str(required=True, validate=validate.Length(min=1, max=64))
    demo = fields.Bool(required=True, validate=validate.Equal(True))  # explicit opt-in


class EcdsaKeygenSchema(Schema):
    curve = fields.Str(load_default="P-256", validate=validate.OneOf(["P-256", "P-384"]))


class EcdsaSignSchema(Schema):
    private_pem = fields.Str(required=True)
    message = fields.Str(required=True, validate=validate.Length(min=1, max=8192))
    mode = fields.Str(
        load_default="fips-186-3",
        validate=validate.OneOf(["fips-186-3", "deterministic-rfc6979"]),
    )


class EcdsaVerifySchema(Schema):
    public_pem = fields.Str(required=True)
    message = fields.Str(required=True, validate=validate.Length(min=1, max=8192))
    signature_b64 = fields.Str(required=True)


class EcdsaKReuseSchema(Schema):
    private_pem = fields.Str(required=True)
    message1 = fields.Str(required=True, validate=validate.Length(min=1, max=512))
    message2 = fields.Str(required=True, validate=validate.Length(min=1, max=512))
    demo = fields.Bool(required=True, validate=validate.Equal(True))


# ---------- helpers ----------


def _b64_bytes(value: str, what: str) -> bytes:
    try:
        return base64.b64decode(value, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise InvalidInputError(f"{what} is not valid base64: {exc}") from exc


def _parse_body(schema: Schema):
    body = request.get_json(silent=True)
    if body is None:
        raise InvalidInputError("Request body must be JSON.")
    try:
        return schema.load(body)
    except ValidationError as err:
        raise InvalidInputError(f"Schema validation failed: {err.messages}") from err


# ---------- AES ----------


@bp.post("/aes/encrypt")
@limiter.limit("10 per minute")
def aes_encrypt():
    args = _parse_body(AesEncryptSchema())
    result = aes_service.encrypt(
        args["plaintext"].encode("utf-8"),
        _b64_bytes(args["key_b64"], "key"),
        args["mode"],
        demo=args["demo"],
    )
    audit_service.record_audit("aes.encrypt", {"mode": result["mode"]})
    return jsonify(data=result)


@bp.post("/aes/decrypt")
@limiter.limit("10 per minute")
def aes_decrypt():
    args = _parse_body(AesDecryptSchema())
    payload = {
        "ciphertext": args["ciphertext_b64"],
        "iv": args["iv_b64"],
        "tag": args["tag_b64"],
        "mode": args["mode"],
    }
    result = aes_service.decrypt(payload, _b64_bytes(args["key_b64"], "key"))
    audit_service.record_audit("aes.decrypt", {"mode": args["mode"]})
    return jsonify(data=result)


# ---------- SHA-3 ----------


@bp.post("/sha3/digest")
@limiter.limit("30 per minute")
def sha3_digest():
    args = _parse_body(Sha3Schema())
    result = sha3_service.digest(args["message"].encode("utf-8"), args["algorithm"])
    audit_service.record_audit("sha3.digest", {"algorithm": args["algorithm"]})
    return jsonify(data=result)


# ---------- Argon2 ----------


@bp.post("/argon2/hash")
@limiter.limit("5 per minute")  # paranoid preset costs ~1 s CPU
def argon2_hash():
    args = _parse_body(Argon2HashSchema())
    result = argon2_service.hash_password(args["password"], args["preset"])
    audit_service.record_audit("argon2.hash", {"preset": args["preset"]})
    return jsonify(data=result)


@bp.post("/argon2/verify")
@limiter.limit("10 per minute")
def argon2_verify():
    args = _parse_body(Argon2VerifySchema())
    result = argon2_service.verify_password(args["hash_string"], args["password"])
    audit_service.record_audit("argon2.verify", {})
    return jsonify(data=result)


# ---------- RSA ----------


@bp.post("/rsa/keygen")
@limiter.limit("5 per minute")  # keygen is the most expensive demo op
def rsa_keygen():
    args = _parse_body(RsaKeygenSchema())
    result = rsa_service.generate_keypair(args["bits"])
    audit_service.record_audit("rsa.keygen", {"bits": args["bits"]})
    return jsonify(data=result)


@bp.post("/rsa/encrypt")
@limiter.limit("10 per minute")
def rsa_encrypt():
    args = _parse_body(RsaEncryptSchema())
    result = rsa_service.encrypt(args["public_pem"], args["plaintext"].encode("utf-8"))
    audit_service.record_audit("rsa.encrypt", {"scheme": "oaep-sha256"})
    return jsonify(data=result)


@bp.post("/rsa/decrypt")
@limiter.limit("10 per minute")
def rsa_decrypt():
    args = _parse_body(RsaDecryptSchema())
    result = rsa_service.decrypt(args["private_pem"], args["ciphertext_b64"])
    audit_service.record_audit("rsa.decrypt", {})
    return jsonify(data=result)


@bp.post("/rsa/sign")
@limiter.limit("10 per minute")
def rsa_sign():
    args = _parse_body(RsaSignSchema())
    result = rsa_service.sign(args["private_pem"], args["message"].encode("utf-8"))
    audit_service.record_audit("rsa.sign", {"scheme": "pss-sha256"})
    return jsonify(data=result)


@bp.post("/rsa/verify")
@limiter.limit("10 per minute")
def rsa_verify():
    args = _parse_body(RsaVerifySchema())
    result = rsa_service.verify(args["public_pem"], args["message"].encode("utf-8"), args["signature_b64"])
    audit_service.record_audit("rsa.verify", {})
    return jsonify(data=result)


@bp.post("/rsa/attack/malleability")
@limiter.limit("5 per minute")
def rsa_malleability():
    args = _parse_body(RsaMalleabilitySchema())
    result = rsa_service.textbook_malleability(
        args["public_pem"], args["private_pem"], args["message"].encode("utf-8")
    )
    audit_service.record_audit("rsa.attack.malleability", {})
    return jsonify(data=result)


# ---------- ECDSA ----------


@bp.post("/ecdsa/keygen")
@limiter.limit("10 per minute")
def ecdsa_keygen():
    args = _parse_body(EcdsaKeygenSchema())
    result = ecdsa_service.generate_keypair(args["curve"])
    audit_service.record_audit("ecdsa.keygen", {"curve": args["curve"]})
    return jsonify(data=result)


@bp.post("/ecdsa/sign")
@limiter.limit("10 per minute")
def ecdsa_sign():
    args = _parse_body(EcdsaSignSchema())
    result = ecdsa_service.sign(args["private_pem"], args["message"].encode("utf-8"), args["mode"])
    audit_service.record_audit("ecdsa.sign", {"mode": args["mode"]})
    return jsonify(data=result)


@bp.post("/ecdsa/verify")
@limiter.limit("10 per minute")
def ecdsa_verify():
    args = _parse_body(EcdsaVerifySchema())
    result = ecdsa_service.verify(args["public_pem"], args["message"].encode("utf-8"), args["signature_b64"])
    audit_service.record_audit("ecdsa.verify", {})
    return jsonify(data=result)


@bp.post("/ecdsa/determinism")
@limiter.limit("10 per minute")
def ecdsa_determinism():
    args = _parse_body(EcdsaSignSchema())
    result = ecdsa_service.determinism_demo(args["private_pem"], args["message"].encode("utf-8"))
    audit_service.record_audit("ecdsa.determinism", {})
    return jsonify(data=result)


@bp.post("/ecdsa/attack/k-reuse")
@limiter.limit("5 per minute")
def ecdsa_k_reuse():
    """The Sony-style leak: sign two messages with ONE fixed k, then recover d."""
    args = _parse_body(EcdsaKReuseSchema())
    keypair = ecdsa_service._import(args["private_pem"], "private_pem")
    curve = "P-256" if keypair.curve == "NIST P-256" else "P-384"
    k = 0xC1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1C1  # the fatal reuse
    msg1, msg2 = args["message1"].encode("utf-8"), args["message2"].encode("utf-8")
    sig1 = ecdsa_service.sign_with_explicit_k(args["private_pem"], msg1, k, curve)
    sig2 = ecdsa_service.sign_with_explicit_k(args["private_pem"], msg2, k, curve)
    # sanity: the explicit-k signatures are real ECDSA — they verify under the standard path
    public_pem = keypair.public_key().export_key(format="PEM")
    ecdsa_service.verify(public_pem, msg1, sig1["signature"])
    recovery = ecdsa_service.recover_key_from_reused_k(msg1, sig1, msg2, sig2, curve)
    recovered_d = recovery["recovered_private_key_d"]
    recovery["recovery_confirmed"] = recovered_d == int(keypair.d)
    recovery["r_shared"] = sig1["r"] == sig2["r"]
    audit_service.record_audit("ecdsa.attack.k-reuse", {"curve": curve})
    return jsonify(data={"signature1": sig1, "signature2": sig2, "recovery": recovery})
