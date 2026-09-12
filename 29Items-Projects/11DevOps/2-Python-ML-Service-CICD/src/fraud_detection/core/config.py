"""Application settings loaded from environment variables (prefix ``FRAUD_``)."""

from __future__ import annotations

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration for the fraud detection service.

    Every field can be overridden via an environment variable named
    ``FRAUD_<FIELD_NAME_UPPERCASE>`` (e.g. ``FRAUD_DATABASE_URL``).
    """

    model_config = SettingsConfigDict(
        env_prefix="FRAUD_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        protected_namespaces=(),
    )

    app_name: str = "fraud-detection-api"
    environment: str = "local"
    debug: bool = False
    log_level: str = "INFO"
    # Local-first default: SQLite needs no server. docker-compose and k8s
    # override this with a PostgreSQL URL (postgresql+psycopg2://...).
    database_url: str = "sqlite:///./fraud.db"
    db_auto_create: bool = True
    mlflow_tracking_uri: str = "http://localhost:5000"
    model_name: str = "fraud-detection"
    model_dir: str = "models"
    ab_test_enabled: bool = True
    ab_traffic_split: int = 10
    drift_psi_threshold: float = 0.2
    drift_window_size: int = 500
    drift_min_rows: int = 50
    auto_retrain_on_drift: bool = True
    retrain_min_interval_minutes: int = 60
    training_data_path: str = ""
    training_samples: int = 20000
    metrics_enabled: bool = True
    api_prefix: str = "/api/v1"


@lru_cache
def get_settings() -> Settings:
    """Return the cached application settings singleton."""
    return Settings()
