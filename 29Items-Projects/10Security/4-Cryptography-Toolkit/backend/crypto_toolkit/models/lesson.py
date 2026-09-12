"""Lesson model — educational content paired with each demo.

Deliberately does NOT store user inputs, keys, or ciphertexts; see
migrations/001_init.sql for the audit_log policy.
"""

from __future__ import annotations

from crypto_toolkit.extensions import db
from crypto_toolkit.models.clock import utc_now


class Lesson(db.Model):
    __tablename__ = "lessons"
    __table_args__ = (
        db.CheckConstraint("topic IN ('aes','rsa','ecdsa','sha3','argon2','tls')", name="ck_lessons_topic"),
    )

    id = db.Column(db.Integer, primary_key=True)
    slug = db.Column(db.String(120), unique=True, nullable=False)
    title = db.Column(db.String(200), nullable=False)
    topic = db.Column(db.String(60), nullable=False, index=True)  # aes|rsa|ecdsa|sha3|argon2|tls
    content_md = db.Column(db.Text, nullable=False, default="")
    demo_endpoint = db.Column(db.String(200))  # e.g. "/api/aes/encrypt"
    order_index = db.Column(db.Integer, nullable=False, default=0)
    created_at = db.Column(db.DateTime, nullable=False, default=utc_now)
    updated_at = db.Column(db.DateTime, nullable=False, default=utc_now, onupdate=utc_now)

    def update_from(self, data: dict) -> Lesson:
        """Apply a validated patch dict; caller commits."""
        for field in ("title", "topic", "content_md", "demo_endpoint", "order_index"):
            if field in data and data[field] is not None:
                setattr(self, field, data[field])
        return self

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "slug": self.slug,
            "title": self.title,
            "topic": self.topic,
            "content_md": self.content_md,
            "demo_endpoint": self.demo_endpoint,
            "order_index": self.order_index,
            "created_at": self.created_at.isoformat() if self.created_at else None,
            "updated_at": self.updated_at.isoformat() if self.updated_at else None,
        }
