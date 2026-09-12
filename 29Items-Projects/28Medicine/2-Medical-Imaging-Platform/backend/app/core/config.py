"""Application configuration — 12-factor settings validated at startup.

A single `Settings` object is the source of truth for all environment-derived
config. Importing `settings` anywhere triggers validation; the app fails fast on
missing/invalid values rather than blowing up deep in a request.
"""

from __future__ import annotations

from functools import lru_cache
from typing import Literal

from pydantic import Field, PostgresDsn, computed_field, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env", env_file_encoding="utf-8", extra="ignore", case_sensitive=False
    )

    # ── App ───────────────────────────────────────────────
    app_env: Literal["development", "staging", "production"] = "development"
    log_level: str = "INFO"
    api_v1_prefix: str = "/api/v1"
    cors_origins: list[str] = Field(default_factory=lambda: ["http://localhost:5173"])

    # ── Security ──────────────────────────────────────────
    jwt_secret_key: str = "change-me"
    jwt_algorithm: str = "HS256"
    access_token_expire_minutes: int = 30
    refresh_token_expire_days: int = 7

    # ── PostgreSQL ────────────────────────────────────────
    postgres_host: str = "localhost"
    postgres_port: int = 5432
    postgres_db: str = "medimaging"
    postgres_user: str = "medimaging"
    postgres_password: str = "change-me"
    # Full DSN override (tests/dev use sqlite+aiosqlite; takes precedence).
    database_url_override: str | None = None

    # ── Object store (MinIO / S3) ─────────────────────────
    s3_endpoint_url: str | None = None  # None → real AWS S3
    s3_region: str = "us-east-1"
    s3_access_key: str = "minioadmin"
    s3_secret_key: str = "minioadmin"
    s3_bucket_dicom: str = "dicom-archive"
    s3_bucket_staging: str = "dicom-staging"
    s3_use_ssl: bool = False
    presign_expiry_seconds: int = 300

    # ── Queue ─────────────────────────────────────────────
    queue_url: str | None = None  # None → in-proc fallback for local dev
    ingest_queue_name: str = "ingest-jobs"
    inference_queue_name: str = "inference-jobs"

    # ── Ingestion ─────────────────────────────────────────
    # When true (dev/single-process), STOW runs ingestion inline. When false,
    # objects are staged and an ingest job is pushed to the queue for workers.
    ingest_inline: bool = True

    # ── ML ────────────────────────────────────────────────
    ml_enabled: bool = True
    ml_service_url: str = "http://localhost:8001"
    ml_model_s3_uri: str = "s3://dicom-models/chexnet/densenet121.pt"
    ml_confidence_threshold: float = 0.5
    # Run inference inline after ingest (dev) vs. enqueue for the ML worker.
    ml_inline: bool = True

    # ── De-identification ─────────────────────────────────
    deidentify_on_ingest: bool = False

    @field_validator("s3_endpoint_url", "queue_url", "database_url_override", mode="before")
    @classmethod
    def _empty_to_none(cls, v: str | None) -> str | None:
        """Treat an empty/whitespace env var the same as unset."""
        if v is None:
            return None
        v = str(v).strip()
        return v or None

    @model_validator(mode="after")
    def _no_insecure_defaults_in_prod(self) -> Settings:
        """Refuse to boot in production with placeholder/dev secrets.

        A forgotten ``JWT_SECRET_KEY`` in prod means tokens are signed with a
        publicly-known key — a full authentication bypass — so we fail fast at
        startup rather than serving with it. Same idea for the DB/object-store
        credentials. In development these defaults are fine.
        """
        if self.app_env != "production":
            return self
        offenders: list[str] = []
        if "change-me" in self.jwt_secret_key or self.jwt_secret_key in {
            "dev-only-change-in-prod",
            "test-secret",
        }:
            offenders.append("JWT_SECRET_KEY")
        if "change-me" in self.postgres_password:
            offenders.append("POSTGRES_PASSWORD")
        if self.s3_access_key == "minioadmin":
            offenders.append("S3_ACCESS_KEY")
        if self.s3_secret_key == "minioadmin":
            offenders.append("S3_SECRET_KEY")
        if offenders:
            raise ValueError(
                "Refusing to start in production with insecure default secrets: "
                f"{', '.join(offenders)}. Provide real values via the environment "
                "or AWS Secrets Manager."
            )
        return self

    @computed_field  # type: ignore[misc]
    @property
    def database_url(self) -> str:
        """Async SQLAlchemy DSN. Honors an explicit override (tests/dev)."""
        if self.database_url_override:
            return self.database_url_override
        return str(
            PostgresDsn.build(
                scheme="postgresql+asyncpg",
                username=self.postgres_user,
                password=self.postgres_password,
                host=self.postgres_host,
                port=self.postgres_port,
                path=self.postgres_db,
            )
        )

    @computed_field  # type: ignore[misc]
    @property
    def is_production(self) -> bool:
        return self.app_env == "production"


@lru_cache
def get_settings() -> Settings:
    """Cached accessor so settings are parsed exactly once per process."""
    return Settings()


settings = get_settings()
