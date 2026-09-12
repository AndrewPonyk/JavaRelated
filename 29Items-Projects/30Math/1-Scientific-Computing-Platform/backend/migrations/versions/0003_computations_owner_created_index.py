"""Composite index for the computations list query.

Revision ID: 0003
Revises: 0002
Create Date: 2026-07-08

GET /computations filters by owner_id and orders by created_at DESC; the
composite index serves both without a sort step.
"""

from alembic import op

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_index(
        "ix_computations_owner_created", "computations", ["owner_id", "created_at"]
    )


def downgrade() -> None:
    op.drop_index("ix_computations_owner_created", table_name="computations")
