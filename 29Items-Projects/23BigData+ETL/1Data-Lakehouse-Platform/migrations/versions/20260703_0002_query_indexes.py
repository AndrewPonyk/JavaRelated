"""Indexes for the list/audit query patterns observed in production use.

Revision ID: 0002
Revises: 0001
Create Date: 2026-07-03
"""

from __future__ import annotations

from alembic import op

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # GET /datasets?layer=... filters on layer
    op.create_index("ix_datasets_layer", "datasets", ["layer"])
    # GET /audit?actor=... filters on actor
    op.create_index("ix_audit_events_actor", "audit_events", ["actor"])


def downgrade() -> None:
    op.drop_index("ix_audit_events_actor", table_name="audit_events")
    op.drop_index("ix_datasets_layer", table_name="datasets")
