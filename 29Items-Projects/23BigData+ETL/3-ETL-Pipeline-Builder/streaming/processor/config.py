"""Processor configuration — every knob documented in .env.example."""

from __future__ import annotations

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # Kafka / MSK
    kafka_bootstrap_servers: str = "localhost:29092"
    kafka_events_topic: str = "events.orders.v1"
    kafka_alerts_topic: str = "alerts.anomaly.v1"
    kafka_dlq_topic: str = "events.deadletter.v1"
    kafka_consumer_group: str = "metrics-processor"

    # Hot store
    redis_url: str = "redis://localhost:6379/0"
    metrics_channel: str = "metrics.updates"
    metrics_key_prefix: str = "metric:"
    metrics_ttl_seconds: int = 3600
    # Rolling live history kept per metric (sparklines / API history endpoint).
    history_retention_minutes: int = 180

    # Windowing — 500 ms is the latency/stability tradeoff knob (ARCHITECTURE §2.3)
    window_ms: int = 500
    allowed_lateness_ms: int = 1000

    # Anomaly detection
    anomaly_alpha: float = 0.05
    anomaly_z_threshold: float = 4.0
    anomaly_warmup: int = 120
    alert_cooldown_seconds: int = 60

    # Tuned-parameter hot reload (published by the anomaly_model_retrain DAG)
    model_poll_seconds: int = 60
    model_key: str = "anomaly:model"

    # Ops
    stats_interval_seconds: int = 30

    # Local-dev warehouse sink (prod ingestion is MSK Connect — see snowflake_sink.py)
    dev_snowflake_sink_enabled: bool = False
