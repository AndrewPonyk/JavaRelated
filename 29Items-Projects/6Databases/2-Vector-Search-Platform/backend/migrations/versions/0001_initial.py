"""initial schema: documents, document_chunks, pgvector chunk_embeddings + indexes

Revision ID: 0001_initial
Revises:
Create Date: 2026-07-01

pgvector's ``vector`` column + HNSW index are created with raw SQL (not core SQLAlchemy DDL).
EMBEDDING_DIM must match the embedding model output.
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op
from app.core.config import get_settings
from sqlalchemy.dialects import postgresql

revision = "0001_initial"
down_revision = None
branch_labels = None
depends_on = None

_DIM = get_settings().embedding_dim


def upgrade() -> None:
    op.execute("CREATE EXTENSION IF NOT EXISTS vector")

    op.create_table(
        "documents",
        sa.Column("id", sa.String(length=64), primary_key=True),
        sa.Column("source", sa.String(length=1024), nullable=True),
        sa.Column("text", sa.Text(), nullable=False),
        sa.Column("metadata", postgresql.JSONB(), nullable=False, server_default="{}"),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False
        ),
    )

    op.create_table(
        "document_chunks",
        sa.Column("id", sa.String(length=80), primary_key=True),
        sa.Column(
            "document_id",
            sa.String(length=64),
            sa.ForeignKey("documents.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("chunk_index", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("text", sa.Text(), nullable=False),
        sa.Column("metadata", postgresql.JSONB(), nullable=False, server_default="{}"),
    )
    op.create_index("ix_document_chunks_document_id", "document_chunks", ["document_id"])
    op.execute(
        "CREATE INDEX ix_document_chunks_fts ON document_chunks "
        "USING gin (to_tsvector('english', text))"
    )

    # pgvector store's table (also created idempotently by PgVectorStore.ensure_ready).
    op.execute(
        f"""
        CREATE TABLE IF NOT EXISTS chunk_embeddings (
            id           TEXT PRIMARY KEY,
            document_id  TEXT NOT NULL,
            chunk_index  INT  NOT NULL DEFAULT 0,
            text         TEXT NOT NULL,
            metadata     JSONB NOT NULL DEFAULT '{{}}',
            embedding    vector({_DIM}) NOT NULL
        )
        """
    )
    op.execute(
        "CREATE INDEX IF NOT EXISTS chunk_embeddings_hnsw ON chunk_embeddings "
        "USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64)"
    )


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS chunk_embeddings")
    op.drop_table("document_chunks")
    op.drop_table("documents")
    # Leave the `vector` extension installed; other schemas may depend on it.
