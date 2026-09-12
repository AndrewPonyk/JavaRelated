"""Platform metadata ORM models (SQLAlchemy 2.0, async-friendly).

These own the *durable* platform state: experiments, training runs, registered
model versions, A/B test definitions, and drift reports. High-volume telemetry
(raw inference samples, A/B outcome streams) is intentionally NOT here — that
belongs in a fast store (Redis/S3/feature store), modelled by ``db/telemetry``.

The same models run on SQLite (dev/test) and PostgreSQL (prod); ``JSON`` columns
are portable across both.

Relationships:
    Experiment 1───* Run
    Experiment 1───* ABTest
"""
from __future__ import annotations

import uuid
from datetime import datetime, timezone

from sqlalchemy import JSON, DateTime, Float, ForeignKey, String, Text
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def new_id() -> str:
    return str(uuid.uuid4())


class Base(DeclarativeBase):
    """Declarative base for all ORM models."""


class Experiment(Base):
    """A data-science experiment (owns runs; mirrors an MLflow experiment)."""

    __tablename__ = "experiments"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    name: Mapped[str] = mapped_column(String(128), unique=True, index=True)
    owner: Mapped[str] = mapped_column(String(255), index=True)
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    tags: Mapped[dict] = mapped_column(JSON, default=dict)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    runs: Mapped[list["Run"]] = relationship(
        back_populates="experiment", cascade="all, delete-orphan"
    )
    ab_tests: Mapped[list["ABTest"]] = relationship(
        back_populates="experiment", cascade="all, delete-orphan"
    )


class Run(Base):
    """A training run within an experiment (mirrors an MLflow run)."""

    __tablename__ = "runs"

    run_id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    experiment_id: Mapped[str] = mapped_column(
        ForeignKey("experiments.id"), index=True
    )
    status: Mapped[str] = mapped_column(String(16))
    framework: Mapped[str] = mapped_column(String(32))
    metrics: Mapped[dict] = mapped_column(JSON, default=dict)
    model_name: Mapped[str] = mapped_column(String(128), index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    experiment: Mapped["Experiment"] = relationship(back_populates="runs")


class ModelVersion(Base):
    """A registered model version (registry stage is the promotion mechanism)."""

    __tablename__ = "model_versions"

    name: Mapped[str] = mapped_column(String(128), primary_key=True, index=True)
    version: Mapped[int] = mapped_column(primary_key=True)
    stage: Mapped[str] = mapped_column(String(16), default="None")
    run_id: Mapped[str] = mapped_column(String(36))
    framework: Mapped[str] = mapped_column(String(32))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class ABTest(Base):
    """Champion vs. challenger traffic-split definition."""

    __tablename__ = "ab_tests"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    experiment_id: Mapped[str | None] = mapped_column(
        ForeignKey("experiments.id"), nullable=True, index=True
    )
    model_name: Mapped[str] = mapped_column(String(128), index=True)
    champion_version: Mapped[int] = mapped_column()
    challenger_version: Mapped[int] = mapped_column()
    challenger_traffic_pct: Mapped[float] = mapped_column(Float, default=10.0)
    is_active: Mapped[bool] = mapped_column(default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    experiment: Mapped["Experiment | None"] = relationship(back_populates="ab_tests")


class DriftReport(Base):
    """Outcome of a drift evaluation against a baseline snapshot."""

    __tablename__ = "drift_reports"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    model_name: Mapped[str] = mapped_column(String(128), index=True)
    method: Mapped[str] = mapped_column(String(16))  # PSI | KS | KL
    score: Mapped[float] = mapped_column(Float)
    threshold: Mapped[float] = mapped_column(Float)
    drifted: Mapped[bool] = mapped_column(default=False)
    evaluated_features: Mapped[int] = mapped_column(default=0)
    evaluated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
