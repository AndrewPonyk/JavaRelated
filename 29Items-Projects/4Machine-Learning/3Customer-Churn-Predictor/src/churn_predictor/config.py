"""Application configuration via environment variables (12-factor).

All environment-specific values are loaded and validated here using
``pydantic-settings``. Import the singleton ``settings`` everywhere instead of
reading ``os.environ`` directly, so config is validated once and centrally.
"""

from __future__ import annotations

import logging
from functools import lru_cache

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Strongly-typed application settings sourced from env / ``.env``."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # --- Runtime ---------------------------------------------------------
    app_env: str = Field(default="development", description="development|staging|production")
    log_level: str = Field(default="INFO")
    random_seed: int = Field(default=42)

    # --- Database --------------------------------------------------------
    # SQLAlchemy URL, e.g. postgresql+psycopg://user:pass@host:5432/churn
    database_url: SecretStr = Field(
        default=SecretStr("postgresql+psycopg://churn:churn@localhost:5432/churn")
    )
    db_pool_size: int = Field(default=5)
    db_max_overflow: int = Field(default=10)

    # --- Model -----------------------------------------------------------
    model_artifact_path: str = Field(default="models/artifacts/model.joblib")
    model_remote_url: str = Field(
        default="", description="Optional remote source to pull the artifact from"
    )

    # --- Tuning ----------------------------------------------------------
    optuna_n_trials: int = Field(default=50)
    optuna_timeout_sec: int = Field(default=900)
    cv_folds: int = Field(default=5)

    def __repr__(self) -> str:  # pragma: no cover - cosmetic; redacts secrets
        return (
            f"Settings(app_env={self.app_env!r}, log_level={self.log_level!r}, "
            f"database_url=<redacted>, model_artifact_path={self.model_artifact_path!r})"
        )


@lru_cache
def get_settings() -> Settings:
    """Return the cached, validated settings singleton."""
    return Settings()


def configure_logging(level: str | None = None) -> None:
    """Configure structured-ish stdlib logging once for the process."""
    lvl = (level or get_settings().log_level).upper()
    logging.basicConfig(
        level=lvl,
        format="%(asctime)s level=%(levelname)s module=%(name)s msg=%(message)s",
    )


# Convenience module-level singleton.
settings = get_settings()
