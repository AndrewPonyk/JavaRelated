"""API key model — CI service-account credentials.

Secrets are SHA-256-hashed at rest; only a short prefix is stored in
cleartext for lookup. The full secret is shown exactly once at creation.
"""

from __future__ import annotations

import hmac
from datetime import UTC, datetime

from sqlalchemy import Boolean, DateTime, ForeignKey, Index, String, select
from sqlalchemy.orm import Mapped, mapped_column

from app.core.security import api_key_prefix, hash_api_key
from app.db.base import Base, TimestampMixin


class ApiKey(Base, TimestampMixin):
    __tablename__ = "api_keys"
    __table_args__ = (Index("ix_api_keys_prefix", "prefix"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(128))
    prefix: Mapped[str] = mapped_column(String(16), unique=True)  # e.g. wss_a1b2c
    secret_hash: Mapped[str] = mapped_column(String(64))  # sha256 hex of full secret
    role: Mapped[str] = mapped_column(String(16), default="scanner")
    created_by: Mapped[int | None] = mapped_column(ForeignKey("users.id"))
    revoked: Mapped[bool] = mapped_column(Boolean, default=False)
    last_used_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    expires_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    @classmethod
    async def find_by_secret(cls, session, secret: str) -> ApiKey | None:
        """Prefix narrows the row, hash confirms it, state validates it."""
        stmt = select(cls).where(cls.prefix == api_key_prefix(secret), cls.revoked.is_(False))
        api_key = (await session.scalars(stmt)).first()
        if api_key is None or not hmac.compare_digest(api_key.secret_hash, hash_api_key(secret)):
            return None
        if api_key.expires_at is not None:
            expires = api_key.expires_at
            if expires.tzinfo is None:  # SQLite returns naive datetimes
                expires = expires.replace(tzinfo=UTC)
            if expires < datetime.now(UTC):
                return None
        api_key.last_used_at = datetime.now(UTC)
        await session.commit()
        return api_key
