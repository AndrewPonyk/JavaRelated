"""ORM models. Schema changes go through Alembic (migrations/) — never create_all in prod.

Design decisions:
- IDs are UUID strings (String(36)): portable across PostgreSQL and SQLite (dev/tests),
  and the app never needs pg-native UUID operators on them.
- JSONB on PostgreSQL, plain JSON elsewhere, via with_variant.
- All timestamps are timezone-aware UTC (docs/TECH-NOTES.md §3.6.19).
- Dataset payloads are parquet blobs, size-capped by DatasetService (max_upload_mb);
  datasets are deduplicated by content_hash (sha256 of the parquet bytes).
"""

from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any

import sqlalchemy as sa
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship

JSONVariant = sa.JSON().with_variant(JSONB(), "postgresql")

#: Experiment lifecycle (guarded in ExperimentRepository.set_status).
EXPERIMENT_STATUSES = ("draft", "running", "completed")


def _new_id() -> str:
    return str(uuid.uuid4())


class Base(DeclarativeBase):
    pass


class Dataset(Base):
    __tablename__ = "datasets"

    id: Mapped[str] = mapped_column(sa.String(36), primary_key=True, default=_new_id)
    name: Mapped[str] = mapped_column(sa.String(200), index=True)
    description: Mapped[str | None] = mapped_column(sa.Text())
    source_type: Mapped[str] = mapped_column(sa.String(20), default="upload")  # upload | demo
    row_count: Mapped[int] = mapped_column(sa.Integer(), default=0)
    column_count: Mapped[int] = mapped_column(sa.Integer(), default=0)
    schema_json: Mapped[dict[str, Any]] = mapped_column(JSONVariant, default=dict)
    payload_parquet: Mapped[bytes | None] = mapped_column(sa.LargeBinary())
    #: sha256 of payload_parquet — dedupes re-saves of identical data.
    content_hash: Mapped[str | None] = mapped_column(sa.String(64), index=True)
    created_by_email: Mapped[str | None] = mapped_column(sa.String(320))
    created_at: Mapped[datetime] = mapped_column(
        sa.DateTime(timezone=True), server_default=sa.func.now()
    )

    runs: Mapped[list[AnalysisRun]] = relationship(
        back_populates="dataset", cascade="all, delete-orphan"
    )


class Experiment(Base):
    __tablename__ = "experiments"

    id: Mapped[str] = mapped_column(sa.String(36), primary_key=True, default=_new_id)
    name: Mapped[str] = mapped_column(sa.String(200), unique=True)
    hypothesis: Mapped[str | None] = mapped_column(sa.Text())
    primary_metric: Mapped[str | None] = mapped_column(sa.String(200))
    status: Mapped[str] = mapped_column(sa.String(20), default="draft")
    #: Pre-registered per-variant sample size — analyses before this n warn as interim looks.
    planned_n_per_variant: Mapped[int | None] = mapped_column(sa.Integer())
    #: Expected traffic share of the first (alphabetical) variant, for the SRM check.
    expected_ratio: Mapped[float] = mapped_column(sa.Float(), default=0.5)
    created_at: Mapped[datetime] = mapped_column(
        sa.DateTime(timezone=True), server_default=sa.func.now()
    )

    runs: Mapped[list[AnalysisRun]] = relationship(back_populates="experiment")


class AnalysisRun(Base):
    __tablename__ = "analysis_runs"

    id: Mapped[str] = mapped_column(sa.String(36), primary_key=True, default=_new_id)
    dataset_id: Mapped[str] = mapped_column(
        sa.ForeignKey("datasets.id", ondelete="CASCADE"), index=True
    )
    experiment_id: Mapped[str | None] = mapped_column(
        sa.ForeignKey("experiments.id", ondelete="SET NULL"), index=True
    )
    kind: Mapped[str] = mapped_column(sa.String(30))  # hypothesis|regression|distribution|ab_test
    params_json: Mapped[dict[str, Any]] = mapped_column(JSONVariant, default=dict)
    results_json: Mapped[dict[str, Any]] = mapped_column(JSONVariant, default=dict)
    status: Mapped[str] = mapped_column(
        sa.String(20), default="succeeded"
    )  # pending|succeeded|failed
    error_message: Mapped[str | None] = mapped_column(sa.Text())
    duration_ms: Mapped[int | None] = mapped_column(sa.Integer())
    created_at: Mapped[datetime] = mapped_column(
        sa.DateTime(timezone=True), server_default=sa.func.now(), index=True
    )

    dataset: Mapped[Dataset] = relationship(back_populates="runs")
    experiment: Mapped[Experiment | None] = relationship(back_populates="runs")
