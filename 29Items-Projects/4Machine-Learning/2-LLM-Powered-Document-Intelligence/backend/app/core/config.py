"""Application configuration.

Twelve-factor config: every tunable is an environment variable, validated once at
startup. Importing ``settings`` anywhere gives a single, cached, typed config object.
"""

from __future__ import annotations

from functools import lru_cache
from typing import Literal

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Strongly-typed application settings sourced from the environment / ``.env``."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # ── App ────────────────────────────────────────────────────────────────
    app_env: Literal["development", "staging", "production", "test"] = "development"
    log_level: str = "INFO"
    api_v1_prefix: str = "/api/v1"
    cors_origins: list[str] = ["http://localhost:3000"]

    # ── Backend selection (the key extensibility seam) ─────────────────────
    # local  → dependency-light, fully functional (no cloud needed) — dev/docker/tests.
    # bedrock→ Claude on Bedrock + Pinecone via LangChain — production.
    rag_backend: Literal["local", "bedrock"] = "local"
    storage_backend: Literal["local", "s3"] = "local"
    # inline → ingest synchronously on upload (local). queue → enqueue to SQS (prod).
    ingest_mode: Literal["inline", "queue"] = "inline"

    # ── AWS / Bedrock ──────────────────────────────────────────────────────
    aws_region: str = "us-east-1"
    # Default Claude model on Bedrock. NOTE: Bedrock IDs carry an `anthropic.` prefix.
    bedrock_model_id: str = "anthropic.claude-opus-4-8"
    bedrock_model_id_fast: str = "anthropic.claude-haiku-4-5"
    bedrock_embed_model_id: str = "amazon.titan-embed-text-v2:0"

    # ── Storage ────────────────────────────────────────────────────────────
    local_storage_dir: str = "./.data/documents"
    s3_bucket: str = "docintel-documents"

    # ── Queue (ingestion) ──────────────────────────────────────────────────
    sqs_queue_url: str = ""

    # ── Pinecone ───────────────────────────────────────────────────────────
    pinecone_api_key: str = ""
    pinecone_index: str = "doc-intelligence"
    pinecone_cloud: str = "aws"
    pinecone_region: str = "us-east-1"
    # Titan Text Embeddings v2 → 1024 dims. Must match the index dimension exactly.
    embedding_dimension: int = 1024
    # Dimension used by the local hashing embedder (independent of the cloud index).
    local_embedding_dimension: int = 512

    # ── PostgreSQL ─────────────────────────────────────────────────────────
    database_url: str = "postgresql+asyncpg://postgres:postgres@localhost:5432/docintel"

    # ── LangSmith (observability) ──────────────────────────────────────────
    langsmith_tracing: bool = False
    langsmith_api_key: str = ""
    langsmith_project: str = "doc-intelligence"

    # ── Auth ───────────────────────────────────────────────────────────────
    jwt_issuer: str = ""
    jwt_audience: str = "doc-intelligence-api"
    jwt_jwks_url: str = ""
    # HS256 secret for locally-minted dev tokens (see core.security.make_dev_token).
    jwt_dev_secret: str = "dev-secret-change-me"

    # ── RAG tuning ─────────────────────────────────────────────────────────
    rag_top_k: int = Field(default=6, ge=1, le=50)
    rag_chunk_size: int = Field(default=1000, ge=100)
    rag_chunk_overlap: int = Field(default=150, ge=0)
    rag_max_tokens: int = Field(default=4096, ge=256)
    max_upload_bytes: int = Field(default=25 * 1024 * 1024, ge=1)

    @property
    def is_production(self) -> bool:
        return self.app_env == "production"

    @model_validator(mode="after")
    def _validate(self) -> Settings:
        # Overlap must be smaller than the chunk size or chunking can't make progress.
        if self.rag_chunk_overlap >= self.rag_chunk_size:
            raise ValueError("rag_chunk_overlap must be smaller than rag_chunk_size")
        # Fail fast on production misconfiguration rather than silently running insecure.
        if self.is_production:
            if not self.jwt_jwks_url:
                raise ValueError("JWT_JWKS_URL is required when APP_ENV=production")
            if self.ingest_mode == "queue" and not self.sqs_queue_url:
                raise ValueError("SQS_QUEUE_URL is required when INGEST_MODE=queue")
        return self


@lru_cache
def get_settings() -> Settings:
    """Return the cached settings singleton."""
    return Settings()


settings = get_settings()
