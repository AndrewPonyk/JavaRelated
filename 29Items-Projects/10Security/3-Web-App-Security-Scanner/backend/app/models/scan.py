"""Scan and scan-target ORM models."""

from __future__ import annotations

import enum
from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy import DateTime, Enum, ForeignKey, Index, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, TimestampMixin, utcnow

if TYPE_CHECKING:
    from app.models.finding import Finding


class ScanStatus(str, enum.Enum):
    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"
    CANCELLED = "cancelled"


class ScanProfile(str, enum.Enum):
    """Scan intensity presets — maps to ZAP policy + SQLMap risk/level."""

    FAST = "fast"  # spider + passive only, sqlmap risk=1/level=1
    STANDARD = "standard"  # baseline active scan, sqlmap risk=1/level=2
    DEEP = "deep"  # full active scan, all XSS payloads, sqlmap risk=2/level=3


# Store str-enum .value (not .name) in PG enums — keeps DB labels lowercase
# and identical to API payloads ("fast", "pending", …).
_VALUES = lambda e: [m.value for m in e]  # noqa: E731


class Scan(Base, TimestampMixin):
    __tablename__ = "scans"
    __table_args__ = (Index("ix_scans_status_created", "status", "created_at"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    target_url: Mapped[str] = mapped_column(String(2048))
    profile: Mapped[ScanProfile] = mapped_column(
        Enum(ScanProfile, name="scan_profile", values_callable=_VALUES),
        default=ScanProfile.STANDARD,
    )
    status: Mapped[ScanStatus] = mapped_column(
        Enum(ScanStatus, name="scan_status", values_callable=_VALUES),
        default=ScanStatus.PENDING,
    )
    started_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    timeout_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    error_detail: Mapped[str | None] = mapped_column(Text)  # JSON: {phase, reason}
    requested_by: Mapped[int | None] = mapped_column(ForeignKey("users.id"))

    findings: Mapped[list[Finding]] = relationship(
        back_populates="scan", cascade="all, delete-orphan"
    )

    @property
    def is_terminal(self) -> bool:
        return self.status in {ScanStatus.COMPLETED, ScanStatus.FAILED, ScanStatus.CANCELLED}


class ScanTarget(Base):
    """Operator-maintained allowlist of in-scope hosts — the scoping control."""

    __tablename__ = "scan_targets"
    __table_args__ = (UniqueConstraint("host_pattern", name="uq_scan_targets_host"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    host_pattern: Mapped[str] = mapped_column(String(255))  # e.g. app.example.com or CIDR
    description: Mapped[str | None] = mapped_column(Text)
    verified_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
