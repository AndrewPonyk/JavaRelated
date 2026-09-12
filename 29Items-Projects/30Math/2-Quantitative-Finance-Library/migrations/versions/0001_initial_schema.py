"""initial schema: calculation_audit, fit_jobs, vol_surfaces

Revision ID: 0001
Revises:
Create Date: 2026-07-10
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "calculation_audit",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("request_id", sa.String(36), nullable=False),
        sa.Column("caller", sa.String(64), nullable=False),
        sa.Column("endpoint", sa.String(128), nullable=False),
        sa.Column("params_hash", sa.String(64), nullable=False),
        sa.Column("seed", sa.Integer, nullable=True),
        sa.Column("latency_ms", sa.Float, nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_calculation_audit_request_id", "calculation_audit", ["request_id"])
    op.create_index("ix_audit_caller_created", "calculation_audit", ["caller", "created_at"])

    op.create_table(
        "fit_jobs",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("caller", sa.String(64), nullable=False),
        sa.Column("status", sa.String(16), nullable=False, server_default="PENDING"),
        sa.Column("detail", sa.Text, nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("finished_at", sa.DateTime(timezone=True), nullable=True),
    )

    op.create_table(
        "vol_surfaces",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column(
            "job_id", sa.String(36), sa.ForeignKey("fit_jobs.id"), nullable=False, unique=True
        ),
        sa.Column("forward", sa.Float, nullable=False),
        sa.Column("model_blob", sa.LargeBinary, nullable=False),
        sa.Column("mse", sa.Float, nullable=False),
        sa.Column("n_quotes", sa.Integer, nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )


def downgrade() -> None:
    op.drop_table("vol_surfaces")
    op.drop_table("fit_jobs")
    op.drop_index("ix_audit_caller_created", table_name="calculation_audit")
    op.drop_index("ix_calculation_audit_request_id", table_name="calculation_audit")
    op.drop_table("calculation_audit")
