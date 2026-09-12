"""Transactional index outbox + impression log on search_events.

Revision ID: 0002
Revises: 0001
Create Date: 2026-07-02
"""

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "index_outbox",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column("product_id", postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column("op", sa.String(8), nullable=False),
        sa.Column("attempts", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("last_error", sa.Text(), nullable=True),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()
        ),
        sa.Column("processed_at", sa.DateTime(timezone=True), nullable=True),
    )
    # Partial index: the worker only ever scans pending rows.
    op.create_index(
        "ix_index_outbox_pending",
        "index_outbox",
        ["id"],
        postgresql_where=sa.text("processed_at IS NULL"),
    )

    op.add_column(
        "search_events",
        sa.Column("shown_product_ids", postgresql.JSONB(), nullable=True),
    )


def downgrade() -> None:
    op.drop_column("search_events", "shown_product_ids")
    op.drop_index("ix_index_outbox_pending", table_name="index_outbox")
    op.drop_table("index_outbox")
