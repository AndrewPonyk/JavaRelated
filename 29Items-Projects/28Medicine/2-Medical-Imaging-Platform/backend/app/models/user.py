"""User — application account with a role (drives RBAC scopes)."""

from __future__ import annotations

from sqlalchemy import Boolean, String
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, TimestampMixin, UUIDMixin


class User(UUIDMixin, TimestampMixin, Base):
    __tablename__ = "users"

    email: Mapped[str] = mapped_column(String(256), unique=True, index=True)
    full_name: Mapped[str | None] = mapped_column(String(256))
    hashed_password: Mapped[str] = mapped_column(String(256))
    # Stored as the Role enum value (see core.security.Role).
    role: Mapped[str] = mapped_column(String(32), default="referring_physician")
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
