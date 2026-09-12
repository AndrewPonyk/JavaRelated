"""SQLAlchemy models — metadata only, never market data (ARCHITECTURE.md §2.2).

Three tables:
    calculation_audit  every pricing/risk call: reproducibility + traceability
    fit_jobs           long-running vol-surface fits (job-queue pattern)
    vol_surfaces       serialized fitted surfaces, queryable after the job
"""

from __future__ import annotations

import uuid
from datetime import datetime, timezone

from sqlalchemy import DateTime, ForeignKey, Index, LargeBinary, String, Text
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


def _uuid() -> str:
    return str(uuid.uuid4())


def _now() -> datetime:
    return datetime.now(timezone.utc)


class Base(DeclarativeBase):
    pass


class CalculationAudit(Base):
    """One row per priced request — makes any historical number re-derivable."""

    __tablename__ = "calculation_audit"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    request_id: Mapped[str] = mapped_column(String(36), index=True)
    caller: Mapped[str] = mapped_column(String(64))  # API-key hash
    endpoint: Mapped[str] = mapped_column(String(128))  # e.g. /v1/options/price
    params_hash: Mapped[str] = mapped_column(String(64))  # sha256 of canonical params
    seed: Mapped[int | None] = mapped_column(nullable=True)  # MC reproducibility
    latency_ms: Mapped[float] = mapped_column()
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)

    __table_args__ = (
        Index("ix_audit_caller_created", "caller", "created_at"),
        # The admin listing is a global ORDER BY created_at DESC; the composite
        # index above cannot serve that without a sort.
        Index("ix_audit_created_at", "created_at"),
    )


class FitJob(Base):
    """Background vol-surface fit job (submit → poll → result)."""

    __tablename__ = "fit_jobs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    caller: Mapped[str] = mapped_column(String(64))
    status: Mapped[str] = mapped_column(
        String(16), default="PENDING", server_default="PENDING"
    )  # PENDING/RUNNING/DONE/FAILED
    detail: Mapped[str | None] = mapped_column(Text, nullable=True)  # error message if FAILED
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)
    finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    surface: Mapped[VolSurface | None] = relationship(back_populates="job")


class VolSurface(Base):
    """A fitted surface: serialized model + fit metrics."""

    __tablename__ = "vol_surfaces"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_uuid)
    job_id: Mapped[str] = mapped_column(ForeignKey("fit_jobs.id"), unique=True)
    forward: Mapped[float] = mapped_column()
    model_blob: Mapped[bytes] = mapped_column(LargeBinary)  # VolSurfaceModel.to_bytes()
    mse: Mapped[float] = mapped_column()
    n_quotes: Mapped[int] = mapped_column()
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_now)

    job: Mapped[FitJob] = relationship(back_populates="surface")
