"""TLS 1.3 demo endpoints — handshake walkthrough, HKDF step, downgrade sim."""

from __future__ import annotations

import binascii

from flask import Blueprint, jsonify, request

from crypto_toolkit.errors import InvalidInputError
from crypto_toolkit.extensions import limiter
from crypto_toolkit.services import audit_service, tls_demo_service

bp = Blueprint("tls", __name__)


@bp.get("/handshake")
@limiter.limit("30 per minute")
def handshake():
    """Full 1-RTT walkthrough with real crypto values, rendered stepwise."""
    suite = request.args.get("suite", tls_demo_service.DEFAULT_SUITE)
    result = tls_demo_service.handshake(suite)
    audit_service.record_audit("tls.handshake", {"suite": suite})
    return jsonify(data=result)


@bp.post("/hkdf")
@limiter.limit("30 per minute")
def hkdf():
    """One real HKDF-Expand-Label step: {ikm_hex, info_label, length} → OKM hex."""
    body = request.get_json(silent=True) or {}
    try:
        ikm = binascii.unhexlify(body.get("ikm_hex", "00" * 32))
    except ValueError as exc:
        raise InvalidInputError(f"ikm_hex is not valid hex: {exc}") from exc
    info = str(body.get("info_label", "tls13 c hs traffic")).encode("utf-8")[:64]
    length = int(body.get("length", 32))
    if not 1 <= length <= 64:
        raise InvalidInputError("length must be in [1, 64].")
    from crypto_toolkit.services import kdf

    okm = kdf.hkdf_expand_label(ikm, label=_strip_tls13(info), context=b"", length=length)
    audit_service.record_audit("tls.hkdf", {})
    return jsonify(
        data={
            "info_label": info.decode("utf-8", errors="replace"),
            "length": length,
            "output_key_material_hex": okm.hex(),
        }
    )


def _strip_tls13(info: bytes) -> str:
    """The service prepends the RFC 8446 'tls13 ' prefix itself."""
    label = info.decode("utf-8", errors="replace")
    return label.removeprefix("tls13 ").strip()


@bp.get("/downgrade")
@limiter.limit("30 per minute")
def downgrade():
    """Version-stripping MITM: the steps, the sentinel, and why it fails."""
    result = tls_demo_service.downgrade_simulation()
    audit_service.record_audit("tls.downgrade", {})
    return jsonify(data=result)
