"""AuditLog model — which demo ran, when, from where.

Policy (ARCHITECTURE §2.5): metadata only. NO plaintexts, keys, ciphertexts,
or passwords are ever written here. `params` carries non-secret knobs only
(e.g. {"mode": "gcm"}). IPs are stored salted-hashed, never raw.
"""

from __future__ import annotations

from sqlalchemy import Index

from crypto_toolkit.extensions import db
from crypto_toolkit.models.clock import utc_now


class AuditLog(db.Model):
    __tablename__ = "audit_log"
    # One composite index serves both "all entries, newest first" (PK covers it)
    # and future op-filtered time-ordered admin queries — cheaper than two singles.
    __table_args__ = (Index("ix_audit_op_created_at", "op", "created_at"),)

    id = db.Column(db.Integer, primary_key=True)
    op = db.Column(db.String(60), nullable=False)
    params = db.Column(db.Text, nullable=False, default="{}")
    ip_hash = db.Column(db.String(64))
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=True)
    created_at = db.Column(db.DateTime, nullable=False, default=utc_now)

    def to_dict(self) -> dict:
        import json

        try:
            params = json.loads(self.params)
        except ValueError:
            params = {}
        return {
            "id": self.id,
            "op": self.op,
            "params": params,
            "user_id": self.user_id,
            "created_at": self.created_at.isoformat() if self.created_at else None,
        }
