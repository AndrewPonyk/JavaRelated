"""index calculation_audit.created_at for the global newest-first listing

Revision ID: 0002
Revises: 0001
Create Date: 2026-07-10
"""

from __future__ import annotations

from alembic import op

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_index("ix_audit_created_at", "calculation_audit", ["created_at"])


def downgrade() -> None:
    op.drop_index("ix_audit_created_at", table_name="calculation_audit")
