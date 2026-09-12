"""Initial catalog schema: datasets, dataset_versions, audit_events.

Revision ID: 0001
Revises: None
Create Date: 2026-07-02
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None

dataset_layer = sa.Enum("bronze", "silver", "gold", name="dataset_layer")


def upgrade() -> None:
    op.create_table(
        "datasets",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("name", sa.String(120), nullable=False),
        sa.Column("layer", dataset_layer, nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("owner_email", sa.String(254), nullable=False),
        sa.Column("s3_path", sa.String(1024), nullable=False),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.Column(
            "updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
    )
    op.create_index("ix_datasets_name", "datasets", ["name"], unique=True)

    op.create_table(
        "dataset_versions",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "dataset_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("datasets.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("version", sa.Integer(), nullable=False),
        sa.Column("schema_json", postgresql.JSONB(), nullable=False),
        sa.Column("row_count", sa.BigInteger(), nullable=True),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.UniqueConstraint("dataset_id", "version", name="uq_dataset_version"),
    )
    op.create_index("ix_dataset_versions_dataset_id", "dataset_versions", ["dataset_id"])

    op.create_table(
        "audit_events",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "occurred_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
        sa.Column("actor", sa.String(254), nullable=False),
        sa.Column("action", sa.String(64), nullable=False),
        sa.Column("entity_type", sa.String(64), nullable=False),
        sa.Column("entity_id", sa.String(64), nullable=False),
        sa.Column("details", postgresql.JSONB(), nullable=True),
    )
    # Audit queries are always "what happened to X (recently)" — index accordingly.
    op.create_index(
        "ix_audit_events_entity",
        "audit_events",
        ["entity_type", "entity_id", "occurred_at"],
    )


def downgrade() -> None:
    op.drop_table("audit_events")
    op.drop_table("dataset_versions")
    op.drop_table("datasets")
    dataset_layer.drop(op.get_bind(), checkfirst=True)
