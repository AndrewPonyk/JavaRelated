"""Application configuration.

12-factor config: everything comes from the environment and is validated **once**
here via pydantic-settings. Import ``get_settings()`` (cached) everywhere else —
never read ``os.environ`` directly in application code.
"""

from __future__ import annotations

from enum import Enum
from functools import lru_cache

from pydantic import Field, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

# API keys that are fine for local dev but must never be used in production.
_INSECURE_API_KEYS = {"", "dev-local-key-change-me", "change-me", "changeme"}


class AppEnv(str, Enum):
    development = "development"
    staging = "staging"
    production = "production"


class Backend(str, Enum):
    """Available vector backends. ``memory`` is a real in-process backend for dev/tests."""

    memory = "memory"
    pgvector = "pgvector"
    pinecone = "pinecone"
    weaviate = "weaviate"
    milvus = "milvus"


class EmbeddingProvider(str, Enum):
    """How embeddings are produced.

    * ``auto`` — use Sentence Transformers if importable + loadable, else hashing.
    * ``sentence-transformers`` — force the real model (fails loudly if unavailable).
    * ``hashing`` — deterministic, dependency-light hashing embedder (offline/tests).
    """

    auto = "auto"
    sentence_transformers = "sentence-transformers"
    hashing = "hashing"


class Settings(BaseSettings):
    """Strongly-typed application settings loaded from env / .env."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # ── App ──────────────────────────────────────────────
    app_env: AppEnv = AppEnv.development
    log_level: str = "INFO"
    api_v1_prefix: str = "/api/v1"
    cors_origins: list[str] = Field(default_factory=lambda: ["http://localhost:3000"])

    # ── Auth ─────────────────────────────────────────────
    api_key: str = "dev-local-key-change-me"

    # ── Datastores ───────────────────────────────────────
    database_url: str = "postgresql+asyncpg://vsp:vsp@localhost:5432/vsp"
    redis_url: str | None = "redis://localhost:6379/0"
    embedding_cache_ttl: int = 86_400

    # ── Embeddings ───────────────────────────────────────
    embedding_provider: EmbeddingProvider = EmbeddingProvider.auto
    embedding_model: str = "sentence-transformers/all-MiniLM-L6-v2"
    embedding_dim: int = 384
    embedding_batch_size: int = 32

    # ── Ingestion / search tuning ────────────────────────
    chunk_size: int = 800
    chunk_overlap: int = 100
    keyword_candidate_limit: int = 500  # rows scanned by portable keyword search

    # ── Backend selection + feature flags ────────────────
    default_backend: Backend = Backend.pgvector
    enable_pinecone: bool = False
    enable_weaviate: bool = False
    enable_milvus: bool = False
    enable_reranker: bool = False

    # ── Pinecone ─────────────────────────────────────────
    pinecone_api_key: str | None = None
    pinecone_index: str = "vsp-index"

    # ── Weaviate ─────────────────────────────────────────
    weaviate_url: str = "http://localhost:8080"
    weaviate_api_key: str | None = None
    weaviate_collection: str = "VspChunk"

    # ── Milvus ───────────────────────────────────────────
    milvus_uri: str = "http://localhost:19530"
    milvus_collection: str = "vsp_collection"

    @field_validator("cors_origins", mode="before")
    @classmethod
    def _split_origins(cls, v: object) -> object:
        # Allow a comma-separated string in addition to a JSON list.
        if isinstance(v, str) and not v.startswith("["):
            return [origin.strip() for origin in v.split(",") if origin.strip()]
        return v

    @model_validator(mode="after")
    def _guard_production_secrets(self) -> Settings:
        # Fail fast: never boot production with a default/blank API key.
        if self.app_env is AppEnv.production and self.api_key.strip() in _INSECURE_API_KEYS:
            raise ValueError(
                "API_KEY must be a strong, non-default value when APP_ENV=production "
                "(inject it from AWS Secrets Manager / SSM)."
            )
        return self

    @property
    def is_production(self) -> bool:
        return self.app_env is AppEnv.production

    @property
    def is_sqlite(self) -> bool:
        return self.database_url.startswith("sqlite")


@lru_cache
def get_settings() -> Settings:
    """Return a process-wide cached Settings instance."""
    return Settings()
