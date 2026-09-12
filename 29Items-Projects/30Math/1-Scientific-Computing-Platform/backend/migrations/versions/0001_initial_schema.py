"""Initial schema: users, computations, notebooks.

Revision ID: 0001
Revises:
Create Date: 2026-07-08

Mirrors app/db/models.py exactly. Prod discipline: expand → migrate → contract;
every migration must be backward-compatible one release back
(docs/TECH-NOTES.md §3.1).
"""

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
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("email", sa.String(320), nullable=False),
        sa.Column("password_hash", sa.String(256), nullable=False),
        sa.Column("role", sa.String(32), nullable=False, server_default="student"),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.text("now()"),
        ),
    )
    op.create_index("ix_users_email", "users", ["email"], unique=True)

    op.create_table(
        "computations",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "owner_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("title", sa.String(200), nullable=False),
        sa.Column("kind", sa.String(32), nullable=False),
        sa.Column("status", sa.String(16), nullable=False, server_default="queued"),
        sa.Column("input_payload", postgresql.JSONB, nullable=False),
        sa.Column("result_payload", postgresql.JSONB, nullable=True),
        sa.Column("error_code", sa.String(64), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.text("now()"),
        ),
        sa.Column("completed_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_index("ix_computations_owner_id", "computations", ["owner_id"])
    op.create_index("ix_computations_status", "computations", ["status"])
    op.create_index("ix_computations_created_at", "computations", ["created_at"])

    op.create_table(
        "notebooks",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "owner_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("title", sa.String(200), nullable=False),
        sa.Column("s3_key", sa.String(1024), nullable=False),
        sa.Column("is_template", sa.Boolean, nullable=False, server_default=sa.false()),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.text("now()"),
        ),
    )
    op.create_index("ix_notebooks_owner_id", "notebooks", ["owner_id"])
    op.create_index("ix_notebooks_s3_key", "notebooks", ["s3_key"], unique=True)


def downgrade() -> None:
    op.drop_table("notebooks")
    op.drop_table("computations")
    op.drop_table("users")
