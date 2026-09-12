"""Typed application settings.

Single configuration mechanism for every runtime (local, docker, ECS, EMR):
environment variables prefixed ``LA_``, optionally loaded from a local ``.env``.
See ``.env.example`` for the documented template.
"""

from __future__ import annotations

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="LA_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # ── Core ──────────────────────────────────────────────────────────────
    app_env: str = "dev"  # dev | staging | prod
    log_level: str = "INFO"

    # ── Kafka ─────────────────────────────────────────────────────────────
    kafka_bootstrap_servers: str = "localhost:29092"
    kafka_client_id: str = "log-analytics"
    # MSK/TLS: PLAINTEXT (local) | SSL | SASL_SSL (+ mechanism PLAIN or SCRAM-SHA-512)
    kafka_security_protocol: str = "PLAINTEXT"
    kafka_sasl_mechanism: str = ""
    kafka_sasl_username: str = ""
    kafka_sasl_password: str = ""

    # ── OpenSearch / Elasticsearch ────────────────────────────────────────
    opensearch_url: str = "http://localhost:9200"
    opensearch_username: str = ""
    opensearch_password: str = ""

    # ── Ingestion gateway ─────────────────────────────────────────────────
    gateway_api_keys: str = ""  # comma-separated; empty disables auth (dev only)
    gateway_max_batch: int = 1000
    gateway_rate_limit_rps: float = 0.0  # sustained events/sec per caller; 0 disables
    gateway_rate_limit_burst: int = 5000  # bucket capacity (events)

    # ── Alerting ──────────────────────────────────────────────────────────
    redis_url: str = "redis://localhost:6379/0"
    slack_webhook_url: str = ""
    pagerduty_routing_key: str = ""
    anomaly_alert_threshold: float = 0.8  # fallback when no anomaly_score rules are configured
    anomaly_critical_threshold: float = 0.95
    alert_dedup_ttl_seconds: int = 300
    rules_path: str = "config/alert_rules.yaml"
    # "Service went silent" detection (alerting engine): 0 disables the periodic check.
    silent_service_check_seconds: int = 60
    silent_service_after_minutes: int = 10

    # ── ML / Spark ────────────────────────────────────────────────────────
    model_registry_uri: str = "./models"  # local dir or s3://bucket/prefix
    spark_checkpoint_dir: str = "./.checkpoints"

    # ── API ───────────────────────────────────────────────────────────────
    rules_backend: str = "auto"  # opensearch | memory | auto (probe, fall back to memory)
    cors_origins: str = ""  # comma-separated origins; empty disables CORS

    @property
    def cors_origin_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]

    @property
    def gateway_api_key_set(self) -> frozenset[str]:
        return frozenset(k.strip() for k in self.gateway_api_keys.split(",") if k.strip())

    @property
    def opensearch_auth(self) -> tuple[str, str] | None:
        if self.opensearch_username:
            return (self.opensearch_username, self.opensearch_password)
        return None


@lru_cache
def get_settings() -> Settings:
    """Process-wide settings singleton (overridable in tests via dependency injection)."""
    return Settings()
