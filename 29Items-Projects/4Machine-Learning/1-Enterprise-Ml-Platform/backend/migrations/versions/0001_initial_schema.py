"""initial platform schema

Revision ID: 0001_initial
Revises:
Create Date: 2026-06-14

Creates experiments, runs, model_versions, ab_tests, and drift_reports.
Hand-written baseline; subsequent migrations should be ``--autogenerate``d
from ``src.db.models``.
"""
from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "0001_initial"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "experiments",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("name", sa.String(128), nullable=False, unique=True),
        sa.Column("owner", sa.String(255), nullable=False, index=True),
        sa.Column("description", sa.Text()),
        sa.Column("tags", sa.JSON(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_table(
        "runs",
        sa.Column("run_id", sa.String(36), primary_key=True),
        sa.Column("experiment_id", sa.String(36), sa.ForeignKey("experiments.id"), index=True),
        sa.Column("status", sa.String(16), nullable=False),
        sa.Column("framework", sa.String(32), nullable=False),
        sa.Column("metrics", sa.JSON(), nullable=False),
        sa.Column("model_name", sa.String(128), nullable=False, index=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_table(
        "model_versions",
        sa.Column("name", sa.String(128), primary_key=True, index=True),
        sa.Column("version", sa.Integer(), primary_key=True),
        sa.Column("stage", sa.String(16), nullable=False),
        sa.Column("run_id", sa.String(36), nullable=False),
        sa.Column("framework", sa.String(32), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_table(
        "ab_tests",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("experiment_id", sa.String(36), sa.ForeignKey("experiments.id"), index=True),
        sa.Column("model_name", sa.String(128), nullable=False, index=True),
        sa.Column("champion_version", sa.Integer(), nullable=False),
        sa.Column("challenger_version", sa.Integer(), nullable=False),
        sa.Column("challenger_traffic_pct", sa.Float(), nullable=False),
        sa.Column("is_active", sa.Boolean(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_table(
        "drift_reports",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("model_name", sa.String(128), nullable=False, index=True),
        sa.Column("method", sa.String(16), nullable=False),
        sa.Column("score", sa.Float(), nullable=False),
        sa.Column("threshold", sa.Float(), nullable=False),
        sa.Column("drifted", sa.Boolean(), nullable=False),
        sa.Column("evaluated_features", sa.Integer(), nullable=False),
        sa.Column("evaluated_at", sa.DateTime(timezone=True), nullable=False),
    )


def downgrade() -> None:
    op.drop_table("drift_reports")
    op.drop_table("ab_tests")
    op.drop_table("model_versions")
    op.drop_table("runs")
    op.drop_table("experiments")
