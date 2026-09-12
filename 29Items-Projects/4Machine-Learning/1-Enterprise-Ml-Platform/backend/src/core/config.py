"""Application configuration via 12-factor environment variables.

Single source of truth for settings. Loaded once and dependency-injected;
never read ``os.environ`` directly elsewhere in the codebase.
"""
from __future__ import annotations

from functools import lru_cache
from typing import Literal

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Strongly-typed platform settings.

    Values are sourced from environment variables (see ``.env.example``).
    Pydantic validates types/required-ness at startup so misconfiguration
    fails fast rather than at first use.
    """

    model_config = SettingsConfigDict(
        env_file=".env", env_file_encoding="utf-8", extra="ignore"
    )

    # --- Core -------------------------------------------------------------
    app_env: Literal["dev", "staging", "prod"] = "dev"
    log_level: Literal["DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"] = "INFO"
    api_port: int = Field(default=8000, ge=1, le=65535)

    # --- Database ---------------------------------------------------------
    # Dev default: file-based SQLite (zero infra, survives restart).
    # Prod: set to postgresql+asyncpg://... (see .env.example / docker-compose).
    database_url: str = Field(default="sqlite+aiosqlite:///./mlplatform.db")

    # --- MLflow -----------------------------------------------------------
    mlflow_tracking_uri: str = "http://localhost:5000"
    mlflow_artifact_bucket: str = "s3://enterprise-ml-artifacts"

    # --- AWS / SageMaker --------------------------------------------------
    aws_region: str = "us-east-1"
    sagemaker_execution_role_arn: str = ""
    sagemaker_default_instance_type: str = "ml.m5.large"

    # --- Cache / online features -----------------------------------------
    redis_url: str = "redis://localhost:6379/0"

    # --- Auth -------------------------------------------------------------
    # "hs256": verify shared-secret service tokens (dev / internal).
    # "oidc":  verify OIDC RS256 tokens via JWKS (production seam).
    # "disabled": accept all callers — forbidden in prod, fails fast below.
    auth_mode: Literal["hs256", "oidc", "disabled"] = "hs256"
    jwt_secret: str = ""
    oidc_issuer: str = ""
    oidc_audience: str = "enterprise-ml-platform"
    jwt_algorithms: list[str] = ["RS256"]

    # --- API behavior -----------------------------------------------------
    cors_allow_origins: list[str] = ["http://localhost:5173"]
    default_page_size: int = Field(default=50, ge=1, le=500)
    max_page_size: int = Field(default=200, ge=1, le=1000)

    @property
    def is_production(self) -> bool:
        return self.app_env == "prod"

    @model_validator(mode="after")
    def _enforce_secure_prod(self) -> "Settings":
        """Fail fast on insecure production configuration."""
        if self.app_env == "prod":
            if self.auth_mode == "disabled":
                raise ValueError("auth_mode='disabled' is not allowed in production")
            if self.auth_mode == "hs256" and not self.jwt_secret:
                raise ValueError("jwt_secret is required when auth_mode='hs256' in production")
            if self.auth_mode == "oidc" and not self.oidc_issuer:
                raise ValueError("oidc_issuer is required when auth_mode='oidc' in production")
            if "*" in self.cors_allow_origins:
                raise ValueError("wildcard CORS origin is not allowed in production")
        if self.auth_mode == "hs256" and not self.jwt_secret:
            # Non-prod convenience: a deterministic, clearly-non-secret dev key.
            object.__setattr__(self, "jwt_secret", "dev-insecure-secret-change-me")
        return self


@lru_cache
def get_settings() -> Settings:
    """Return the cached singleton settings instance."""
    return Settings()
