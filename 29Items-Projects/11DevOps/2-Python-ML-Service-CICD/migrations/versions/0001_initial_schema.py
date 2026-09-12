"""Initial schema: predictions, model_versions, ab_assignments, drift_reports.

Revision ID: 0001
Revises:
Create Date: 2026-07-12

"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

# revision identifiers, used by Alembic.
revision: str = "0001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Create the four canonical fraud-detection tables."""
    op.create_table(
        "predictions",
        sa.Column("id", sa.Uuid(), primary_key=True, nullable=False),
        sa.Column("transaction_id", sa.String(), nullable=False),
        sa.Column("account_id", sa.String(), nullable=False),
        sa.Column("amount", sa.Numeric(18, 2), nullable=False),
        sa.Column("model_version", sa.String(), nullable=False),
        sa.Column("variant", sa.String(), nullable=False),
        sa.Column("fraud_probability", sa.Float(), nullable=False),
        sa.Column("is_fraud", sa.Boolean(), nullable=False),
        sa.Column("latency_ms", sa.Float(), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
    )
    op.create_index(
        "ix_predictions_transaction_id", "predictions", ["transaction_id"], unique=True
    )
    op.create_index("ix_predictions_account_id", "predictions", ["account_id"])

    op.create_table(
        "model_versions",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True, nullable=False),
        sa.Column("name", sa.String(), nullable=False),
        sa.Column("version", sa.String(), nullable=False),
        sa.Column("stage", sa.String(), nullable=False),
        sa.Column("auc", sa.Float(), nullable=True),
        sa.Column("registered_at", sa.DateTime(timezone=True), nullable=False),
    )

    op.create_table(
        "ab_assignments",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True, nullable=False),
        sa.Column("account_id", sa.String(), nullable=False),
        sa.Column("variant", sa.String(), nullable=False),
        sa.Column("model_version", sa.String(), nullable=False),
        sa.Column("assigned_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_ab_assignments_account_id", "ab_assignments", ["account_id"])

    op.create_table(
        "drift_reports",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True, nullable=False),
        sa.Column("feature_name", sa.String(), nullable=False),
        sa.Column("psi_score", sa.Float(), nullable=False),
        sa.Column("threshold", sa.Float(), nullable=False),
        sa.Column("drift_detected", sa.Boolean(), nullable=False),
        sa.Column("window_start", sa.DateTime(timezone=True), nullable=False),
        sa.Column("window_end", sa.DateTime(timezone=True), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )


def downgrade() -> None:
    """Drop all tables created by this revision (reverse order)."""
    op.drop_table("drift_reports")
    op.drop_index("ix_ab_assignments_account_id", table_name="ab_assignments")
    op.drop_table("ab_assignments")
    op.drop_table("model_versions")
    op.drop_index("ix_predictions_account_id", table_name="predictions")
    op.drop_index("ix_predictions_transaction_id", table_name="predictions")
    op.drop_table("predictions")
