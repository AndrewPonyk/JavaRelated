"""initial schema: categories + prediction_logs

Revision ID: 0001_initial
Revises:
Create Date: 2026-06-15
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
        "categories",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("name", sa.String(length=128), nullable=False, unique=True),
        sa.Column(
            "parent_id",
            sa.Integer(),
            sa.ForeignKey("categories.id", ondelete="SET NULL"),
            nullable=True,
        ),
        sa.Column("description", sa.String(length=512), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
    )
    op.create_index("ix_categories_name", "categories", ["name"], unique=True)

    op.create_table(
        "prediction_logs",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("image_hash", sa.String(length=64), nullable=False),
        sa.Column("model_version", sa.String(length=32), nullable=False),
        sa.Column("top_label", sa.String(length=128), nullable=False),
        sa.Column("top_score", sa.Float(), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
    )
    op.create_index("ix_prediction_logs_image_hash", "prediction_logs", ["image_hash"])
    op.create_index("ix_prediction_logs_model_version", "prediction_logs", ["model_version"])


def downgrade() -> None:
    op.drop_table("prediction_logs")
    op.drop_index("ix_categories_name", table_name="categories")
    op.drop_table("categories")
