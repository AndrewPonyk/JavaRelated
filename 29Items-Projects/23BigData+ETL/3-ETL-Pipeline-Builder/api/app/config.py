"""API configuration — keys documented in .env.example."""

from __future__ import annotations

from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    service_name: str = "etl-pipeline-builder-api"

    # Hot store (must mirror the processor's sink settings)
    redis_url: str = "redis://localhost:6379/0"
    metrics_channel: str = "metrics.updates"
    metrics_key_prefix: str = "metric:"

    # Metric history: "redis" serves the rolling live window (and the daily
    # cache warmed by Airflow); "snowflake" serves daily buckets from the marts.
    history_source: Literal["redis", "snowflake"] = "redis"

    # Pipeline registry backend: Redis locally, RAW.METADATA.PIPELINES in cloud.
    pipeline_store: Literal["redis", "snowflake"] = "redis"

    # Anomaly alert feed (fed by a background consumer of the alerts topic)
    kafka_bootstrap_servers: str = "localhost:29092"
    kafka_alerts_topic: str = "alerts.anomaly.v1"
    alerts_consumer_enabled: bool = True
    alerts_recent_cap: int = 500

    # Warehouse (history queries; read-only role)
    snowflake_account: str = ""
    snowflake_user: str = ""
    snowflake_password: str = ""  # cloud envs use key-pair auth via secrets
    snowflake_private_key_path: str = ""
    snowflake_role: str = "REPORTER"
    snowflake_warehouse: str = "SERVE_WH"
    snowflake_database: str = "ANALYTICS"

    # API security (ARCHITECTURE §2.5): none (local) | api_key | jwt (any OIDC IdP)
    auth_mode: Literal["none", "api_key", "jwt"] = "none"
    api_key: str = ""
    jwt_secret: str = ""  # HS256 (shared-secret deployments / tests)
    jwt_jwks_url: str = ""  # RS256 via IdP JWKS (Cognito, Entra, Keycloak, ...)
    jwt_audience: str = ""
    jwt_issuer: str = ""

    # Comma-separated (never JSON-in-env; see TECH-NOTES §3.4)
    cors_origins: str = "http://localhost:4200"

    @property
    def cors_origins_list(self) -> list[str]:
        return [origin.strip() for origin in self.cors_origins.split(",") if origin.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
