"""AuditLog — append-only PHI access trail (HIPAA §164.312(b)).

Every create/read/update/delete that touches PHI writes one row: who, what,
when, from where, and the outcome. Never updated or deleted in place.
"""

from __future__ import annotations

from sqlalchemy import String
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, IPType, TimestampMixin, UUIDMixin


class AuditLog(UUIDMixin, TimestampMixin, Base):
    __tablename__ = "audit_log"

    actor_id: Mapped[str | None] = mapped_column(String(64), index=True)  # user id / "system"
    action: Mapped[str] = mapped_column(String(32))  # read|create|update|delete
    resource_type: Mapped[str] = mapped_column(String(32))  # study|series|instance|ml
    resource_id: Mapped[str | None] = mapped_column(String(64), index=True)
    outcome: Mapped[str] = mapped_column(String(16), default="allow")  # allow|deny|error
    source_ip: Mapped[str | None] = mapped_column(IPType)
    correlation_id: Mapped[str | None] = mapped_column(String(64), index=True)
