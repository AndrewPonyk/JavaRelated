"""User model — optional accounts; demos stay anonymous by default.

First registered user becomes admin (documented bootstrap). TOTP secret is
stored encrypted at rest with a key derived from SECRET_KEY (see
auth_service).
"""

from __future__ import annotations

from crypto_toolkit.extensions import db
from crypto_toolkit.models.clock import utc_now


class User(db.Model):
    __tablename__ = "users"

    id = db.Column(db.Integer, primary_key=True)
    email = db.Column(db.String(320), unique=True, nullable=False)  # UNIQUE -> indexed
    password_hash = db.Column(db.Text, nullable=False)  # Argon2id
    is_admin = db.Column(db.Boolean, nullable=False, default=False)
    totp_secret_encrypted = db.Column(db.Text)  # Fernet ciphertext, nullable
    # RFC 6238 §5.2 replay guard: newest timestep whose code we accepted
    totp_last_timestep = db.Column(db.Integer)
    created_at = db.Column(db.DateTime, nullable=False, default=utc_now)

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "email": self.email,
            "is_admin": bool(self.is_admin),
            "totp_enabled": bool(self.totp_secret_encrypted),
            "created_at": self.created_at.isoformat() if self.created_at else None,
        }
