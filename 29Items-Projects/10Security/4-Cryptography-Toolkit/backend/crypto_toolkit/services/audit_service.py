"""Audit service — record demo operations (metadata only, never payloads)."""

from __future__ import annotations

import hashlib
import json

from flask import current_app, has_request_context, request

from crypto_toolkit.extensions import db
from crypto_toolkit.models import AuditLog
from crypto_toolkit.services import auth_service

# Safety net: any key from this list that appears in params is dropped.
_FORBIDDEN_PARAM_KEYS = frozenset(
    {
        "plaintext",
        "ciphertext",
        "key",
        "key_b64",
        "private_pem",
        "public_pem",
        "password",
        "hash",
        "hash_string",
        "signature",
        "tag",
        "secret",
        "token",
    }
)


def record_audit(op: str, params: dict | None = None, user=None) -> None:
    """Persist an audit row. Best-effort: never let audit failures break a demo."""
    try:
        safe_params = {k: v for k, v in (params or {}).items() if k not in _FORBIDDEN_PARAM_KEYS}
        user_id = None
        if user is not None:
            user_id = user.id
        elif has_request_context():
            try:
                current = auth_service.authenticate_request()
                user_id = current.id if current else None
            except Exception:
                user_id = None  # invalid token: log anonymously
        ip_hash = _hash_ip(request.remote_addr) if has_request_context() else None
        db.session.add(AuditLog(op=op, params=json.dumps(safe_params), ip_hash=ip_hash, user_id=user_id))
        db.session.commit()
    except Exception:  # noqa: BLE001 — audit is never allowed to break the request
        db.session.rollback()


def _hash_ip(ip: str | None) -> str | None:
    if not ip:
        return None
    salt = current_app.config.get("AUDIT_IP_SALT") or current_app.config["SECRET_KEY"]
    return hashlib.sha256((salt + ip).encode()).hexdigest()


def list_audit(limit: int = 100, offset: int = 0) -> dict:
    limit = max(1, min(limit, 500))
    query = AuditLog.query.order_by(AuditLog.id.desc())
    total = query.count()
    rows = query.offset(offset).limit(limit).all()
    return {"total": total, "items": [row.to_dict() for row in rows]}
