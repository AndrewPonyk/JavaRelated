"""dataset content hash + experiment planning fields

Revision ID: 0002
Revises: 0001
Create Date: 2026-07-11

- datasets.content_hash: sha256 of the parquet payload — dedupes re-saves.
- experiments.planned_n_per_variant / expected_ratio: pre-registration fields
  that power the interim-look warning and the SRM check.

Backward-compatible (expand step): all new columns are nullable or defaulted,
so the previous app version keeps working during rollout (TECH-NOTES §3.6.20).
"""

import sqlalchemy as sa
from alembic import op

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("datasets", sa.Column("content_hash", sa.String(64), nullable=True))
    op.create_index("ix_datasets_content_hash", "datasets", ["content_hash"])

    op.add_column(
        "experiments", sa.Column("planned_n_per_variant", sa.Integer(), nullable=True)
    )
    op.add_column(
        "experiments",
        sa.Column("expected_ratio", sa.Float(), nullable=False, server_default="0.5"),
    )


def downgrade() -> None:
    op.drop_column("experiments", "expected_ratio")
    op.drop_column("experiments", "planned_n_per_variant")
    op.drop_index("ix_datasets_content_hash", table_name="datasets")
    op.drop_column("datasets", "content_hash")
