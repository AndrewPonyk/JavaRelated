"""API settings — environment first, `.env` for local dev, safe local defaults."""

from __future__ import annotations

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    database_url: str = "postgresql+psycopg://lakehouse:lakehouse@localhost:5432/catalog"
    api_cors_origins: str = "http://localhost:5173"

    # --- Trino (dataset previews) ---
    trino_host: str = "localhost"
    trino_port: int = 8081
    trino_user: str = "catalog-api"
    trino_catalog: str = "delta"
    trino_http_scheme: str = "http"
    preview_row_limit: int = 100

    # --- Authentication ---
    # Empty AUTH_JWKS_URL selects development mode: every request acts as a
    # local platform-admin. Any non-empty value enforces JWT validation.
    auth_jwks_url: str = ""
    auth_audience: str = "lakehouse-console"
    auth_roles_claim: str = "roles"  # Cognito uses "cognito:groups"

    # --- Audit fan-out to Kafka (best-effort; DB row is the source of truth) ---
    kafka_audit_enabled: bool = False
    kafka_bootstrap_servers: str = "localhost:9094"
    kafka_audit_topic: str = "platform.audit.v1"

    @property
    def cors_origins(self) -> list[str]:
        return [origin.strip() for origin in self.api_cors_origins.split(",") if origin.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
