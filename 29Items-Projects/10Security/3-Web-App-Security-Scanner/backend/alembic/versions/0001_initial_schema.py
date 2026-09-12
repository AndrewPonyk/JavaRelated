"""initial schema: users, scans, scan_targets, findings

Revision ID: 0001
Revises:
Create Date: 2026-09-10
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "users",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("email", sa.String(255), nullable=False, unique=True, index=True),
        sa.Column("full_name", sa.String(255), nullable=True),
        sa.Column("hashed_password", sa.String(512), nullable=False),
        sa.Column("role", sa.String(16), nullable=False, server_default="viewer"),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default=sa.true()),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
    )

    scan_status = sa.Enum(
        "pending", "running", "completed", "failed", "cancelled", name="scan_status"
    )
    scan_profile = sa.Enum("fast", "standard", "deep", name="scan_profile")
    op.create_table(
        "scans",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("target_url", sa.String(2048), nullable=False),
        sa.Column("profile", scan_profile, nullable=False, server_default="standard"),
        sa.Column("status", scan_status, nullable=False, server_default="pending"),
        sa.Column("started_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("finished_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("timeout_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("error_detail", sa.Text(), nullable=True),
        sa.Column("requested_by", sa.Integer(), sa.ForeignKey("users.id"), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_scans_status_created", "scans", ["status", "created_at"])

    op.create_table(
        "scan_targets",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("host_pattern", sa.String(255), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("verified_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.UniqueConstraint("host_pattern", name="uq_scan_targets_host"),
    )

    finding_severity = sa.Enum(
        "info", "low", "medium", "high", "critical", name="finding_severity"
    )
    finding_source = sa.Enum("zap", "sqlmap", "xss_engine", name="finding_source")
    op.create_table(
        "findings",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column(
            "scan_id", sa.Integer(), sa.ForeignKey("scans.id", ondelete="CASCADE"), nullable=False
        ),
        sa.Column("source", finding_source, nullable=False),
        sa.Column("rule_id", sa.String(128), nullable=False),
        sa.Column("title", sa.String(512), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("url", sa.String(2048), nullable=False),
        sa.Column("param", sa.String(255), nullable=True),
        sa.Column("method", sa.String(10), nullable=True),
        sa.Column("severity", finding_severity, nullable=False),
        sa.Column("severity_confidence", sa.Float(), nullable=True),
        sa.Column("owasp_category", sa.String(8), nullable=True),
        sa.Column("cwe_id", sa.String(16), nullable=True),
        sa.Column("dedup_hash", sa.String(64), nullable=False),
        sa.Column("evidence", postgresql.JSONB(), nullable=False, server_default="{}"),
        sa.Column("raw", postgresql.JSONB(), nullable=False, server_default="{}"),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.UniqueConstraint("scan_id", "dedup_hash", name="uq_findings_scan_dedup"),
    )
    op.create_index("ix_findings_scan_severity", "findings", ["scan_id", "severity"])


def downgrade() -> None:
    op.drop_table("findings")
    sa.Enum(name="finding_source").drop(op.get_bind(), checkfirst=True)
    sa.Enum(name="finding_severity").drop(op.get_bind(), checkfirst=True)
    op.drop_table("scan_targets")
    op.drop_table("scans")
    sa.Enum(name="scan_profile").drop(op.get_bind(), checkfirst=True)
    sa.Enum(name="scan_status").drop(op.get_bind(), checkfirst=True)
    op.drop_table("users")
