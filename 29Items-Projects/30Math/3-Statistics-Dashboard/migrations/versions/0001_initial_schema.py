"""initial schema: datasets, experiments, analysis_runs

Revision ID: 0001
Revises:
Create Date: 2026-07-11

Reviewed by hand — autogenerate does not fully understand JSON variants and
server defaults (docs/TECH-NOTES.md §3.6.18).
"""

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "datasets",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("name", sa.String(200), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("source_type", sa.String(20), nullable=False, server_default="upload"),
        sa.Column("row_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("column_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column(
            "schema_json",
            postgresql.JSONB(),
            nullable=False,
            server_default=sa.text("'{}'::jsonb"),
        ),
        sa.Column("payload_parquet", sa.LargeBinary(), nullable=True),
        sa.Column("created_by_email", sa.String(320), nullable=True),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()
        ),
    )
    op.create_index("ix_datasets_name", "datasets", ["name"])

    op.create_table(
        "experiments",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("name", sa.String(200), nullable=False, unique=True),
        sa.Column("hypothesis", sa.Text(), nullable=True),
        sa.Column("primary_metric", sa.String(200), nullable=True),
        sa.Column("status", sa.String(20), nullable=False, server_default="draft"),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()
        ),
    )

    op.create_table(
        "analysis_runs",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column(
            "dataset_id",
            sa.String(36),
            sa.ForeignKey("datasets.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "experiment_id",
            sa.String(36),
            sa.ForeignKey("experiments.id", ondelete="SET NULL"),
            nullable=True,
        ),
        sa.Column("kind", sa.String(30), nullable=False),
        sa.Column(
            "params_json",
            postgresql.JSONB(),
            nullable=False,
            server_default=sa.text("'{}'::jsonb"),
        ),
        sa.Column(
            "results_json",
            postgresql.JSONB(),
            nullable=False,
            server_default=sa.text("'{}'::jsonb"),
        ),
        sa.Column("status", sa.String(20), nullable=False, server_default="succeeded"),
        sa.Column("error_message", sa.Text(), nullable=True),
        sa.Column("duration_ms", sa.Integer(), nullable=True),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()
        ),
    )
    op.create_index("ix_analysis_runs_dataset_id", "analysis_runs", ["dataset_id"])
    op.create_index("ix_analysis_runs_experiment_id", "analysis_runs", ["experiment_id"])
    op.create_index("ix_analysis_runs_created_at", "analysis_runs", ["created_at"])


def downgrade() -> None:
    op.drop_table("analysis_runs")
    op.drop_table("experiments")
    op.drop_table("datasets")
