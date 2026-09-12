"""initial schema: documents + query_logs

Revision ID: 0001_initial
Revises:
Create Date: 2026-06-14

Example baseline migration (deliverable 4.3). In practice, generate with
``alembic revision --autogenerate``; this hand-written version documents the shape.
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
        "documents",
        sa.Column("id", sa.String(length=36), primary_key=True),
        sa.Column("tenant_id", sa.String(length=64), nullable=False),
        sa.Column("filename", sa.String(length=512), nullable=False),
        sa.Column("s3_key", sa.String(length=1024), nullable=False),
        sa.Column("doc_type", sa.String(length=32), nullable=False, server_default="general"),
        sa.Column(
            "status",
            sa.Enum("PENDING", "INDEXED", "FAILED", name="documentstatus"),
            nullable=False,
            server_default="PENDING",
        ),
        sa.Column("chunk_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_documents_tenant_id", "documents", ["tenant_id"])
    op.create_index("ix_documents_tenant_status", "documents", ["tenant_id", "status"])

    op.create_table(
        "query_logs",
        sa.Column("id", sa.String(length=36), primary_key=True),
        sa.Column("tenant_id", sa.String(length=64), nullable=False),
        sa.Column("question", sa.Text(), nullable=False),
        sa.Column("answer", sa.Text(), nullable=False),
        sa.Column("citations", sa.JSON(), nullable=False),
        sa.Column("model_id", sa.String(length=128), nullable=False),
        sa.Column("latency_ms", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("feedback", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_query_logs_tenant_id", "query_logs", ["tenant_id"])
    op.create_index("ix_query_logs_tenant_created", "query_logs", ["tenant_id", "created_at"])


def downgrade() -> None:
    op.drop_index("ix_query_logs_tenant_created", table_name="query_logs")
    op.drop_index("ix_query_logs_tenant_id", table_name="query_logs")
    op.drop_table("query_logs")
    op.drop_index("ix_documents_tenant_status", table_name="documents")
    op.drop_index("ix_documents_tenant_id", table_name="documents")
    op.drop_table("documents")
    sa.Enum(name="documentstatus").drop(op.get_bind(), checkfirst=True)
