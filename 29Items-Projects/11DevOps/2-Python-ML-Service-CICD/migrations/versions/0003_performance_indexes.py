"""Indexes for the hot read paths (list/recent scans and version lookups).

Revision ID: 0003
Revises: 0002
Create Date: 2026-07-12

predictions.created_at    - ordering key for pagination and drift windows
drift_reports.created_at  - ordering key for report history
model_versions.version    - point lookups on promote/delete/catalog merge
"""

from typing import Sequence, Union

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "0003"
down_revision: Union[str, None] = "0002"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Add read-path indexes."""
    op.create_index("ix_predictions_created_at", "predictions", ["created_at"])
    op.create_index("ix_drift_reports_created_at", "drift_reports", ["created_at"])
    op.create_index("ix_model_versions_version", "model_versions", ["version"])


def downgrade() -> None:
    """Drop the read-path indexes."""
    op.drop_index("ix_model_versions_version", table_name="model_versions")
    op.drop_index("ix_drift_reports_created_at", table_name="drift_reports")
    op.drop_index("ix_predictions_created_at", table_name="predictions")
