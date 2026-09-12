"""Typed runtime configuration.

Everything comes from the environment (or `.env` locally); `.env.example` at the
repo root is the authoritative list of variables. Code never branches on
environment *names* — it reads these settings.
"""

from __future__ import annotations

from functools import lru_cache

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

_PLACEHOLDER_PREFIX = "changeme"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # --- runtime ---
    environment: str = "development"  # development | staging | production
    log_level: str = "INFO"
    log_format: str = "plain"  # plain | json

    # --- api ---
    api_host: str = "0.0.0.0"
    api_port: int = 8000
    cors_origins: str = "http://localhost:5173"  # comma-separated
    # ≥32 bytes even as a placeholder (RFC 7518 HS256 minimum); prod refuses it anyway.
    jwt_secret: str = "changeme-dev-only-jwt-secret-not-for-production"
    jwt_algorithm: str = "HS256"
    access_token_expire_minutes: int = 30
    # Bootstrap dashboard admin, created at startup IF NOT EXISTS.
    # Set ADMIN_PASSWORD to empty to disable bootstrapping entirely.
    admin_username: str = "admin"
    admin_password: str = "changeme-admin"
    # Shared gateway ingest key (site gateways, dev, seeding). Per-device keys
    # ("<device_id>.<secret>") are issued at registration and preferred.
    device_api_key: str = "changeme-dev-only-shared-key"
    max_ingest_batch_size: int = 5000

    # --- ingest policy ---
    ingest_max_age_hours: int = 24  # points older than this are rejected
    ingest_future_tolerance_minutes: int = 10  # allowance for device clock skew
    ingest_rate_limit_per_minute: int = 0  # points per device per minute; 0 = off

    # --- cassandra ---
    cassandra_contact_points: str = "127.0.0.1"  # comma-separated
    cassandra_port: int = 9042
    cassandra_keyspace: str = "tsa"
    cassandra_local_dc: str = "datacenter1"
    cassandra_username: str | None = None
    cassandra_password: str | None = None
    rollup_query_threshold_hours: int = 48
    max_series_points: int = 5000  # responses larger than this are decimated

    # --- redis ---
    redis_url: str = "redis://localhost:6379/0"
    live_window_seconds: int = 60
    live_key_ttl_seconds: int = 900
    device_cache_ttl_seconds: int = 60  # ingest-permission cache

    # --- influxdb ---
    influxdb_url: str = "http://localhost:8086"
    influxdb_org: str = "tsa"
    influxdb_bucket: str = "platform_metrics"
    influxdb_token: str = "changeme-dev-token"

    # --- anomaly detection / workers ---
    detection_interval_seconds: int = 300
    detection_method: str = "auto"  # auto | prophet | zscore
    prophet_interval_width: float = 0.95
    zscore_threshold: float = 3.0
    min_history_points: int = 48
    # Fleet sharding across worker replicas: this replica handles devices where
    # crc32(device_id) % shard_count == shard_index.
    worker_shard_index: int = 0
    worker_shard_count: int = 1

    @model_validator(mode="after")
    def _refuse_placeholder_secrets_in_production(self) -> Settings:
        """Fail fast rather than run production with template credentials.

        This is the one place ENVIRONMENT gates behavior — a safety rail,
        per the module docstring. Empty ADMIN_PASSWORD stays legal (it just
        disables admin bootstrapping).
        """
        if self.environment != "production":
            return self
        offending = [
            name
            for name, value in (
                ("JWT_SECRET", self.jwt_secret),
                ("ADMIN_PASSWORD", self.admin_password),
                ("DEVICE_API_KEY", self.device_api_key),
            )
            if value.startswith(_PLACEHOLDER_PREFIX)
        ]
        if offending:
            raise ValueError(
                "Refusing to start in production with placeholder secrets: "
                + ", ".join(offending)
                + ". Set real values in the environment (see .env.example)."
            )
        return self

    @property
    def cors_origins_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]

    @property
    def cassandra_contact_points_list(self) -> list[str]:
        return [h.strip() for h in self.cassandra_contact_points.split(",") if h.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
