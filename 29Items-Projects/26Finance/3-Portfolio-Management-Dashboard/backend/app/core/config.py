"""Application configuration (12-factor, typed).

All runtime config is read **once** from the environment into a cached
``Settings`` instance. The rest of the codebase imports ``get_settings()`` rather
than reading ``os.environ`` directly, so behavior is driven by typed settings —
not scattered ``if ENV == "prod"`` checks.
"""

from __future__ import annotations

from functools import lru_cache

from pydantic import Field, PostgresDsn, computed_field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # --- App ---
    app_env: str = Field(default="development")
    app_name: str = "Portfolio Management Dashboard"
    api_v1_prefix: str = "/api/v1"
    log_level: str = "INFO"
    secret_key: str = Field(default="change-me-in-production-min-32-bytes")
    access_token_expire_minutes: int = 30
    backend_cors_origins: list[str] = Field(default_factory=lambda: ["http://localhost:5173"])

    # --- Database ---
    database_url: str | None = None
    postgres_host: str = "db"
    postgres_port: int = 5432
    postgres_user: str = "portfolio"
    postgres_password: str = "portfolio"
    postgres_db: str = "portfolio"

    # --- Redis / Celery ---
    redis_url: str = "redis://redis:6379/0"
    celery_broker_url: str = "redis://redis:6379/1"
    celery_result_backend: str = "redis://redis:6379/2"
    # When true, simulation jobs run inline instead of via a Celery worker
    # (used by tests and single-process local dev with no broker).
    celery_task_always_eager: bool = False

    # --- Caching (Redis) ---
    cache_enabled: bool = True
    cache_ttl_seconds: int = 900

    # --- Rate limiting ---
    rate_limit_enabled: bool = True
    rate_limit_per_minute: int = 600

    # --- Market data ---
    market_data_provider: str = "stub"
    market_data_api_key: str | None = None

    # --- Quant defaults ---
    risk_free_rate: float = 0.02
    trading_days_per_year: int = 252

    @computed_field  # type: ignore[misc]
    @property
    def sqlalchemy_database_uri(self) -> str:
        """Prefer an explicit DATABASE_URL; otherwise assemble from parts."""
        if self.database_url:
            return self.database_url
        return str(
            PostgresDsn.build(
                scheme="postgresql+psycopg",
                username=self.postgres_user,
                password=self.postgres_password,
                host=self.postgres_host,
                port=self.postgres_port,
                path=self.postgres_db,
            )
        )

    @property
    def is_production(self) -> bool:
        return self.app_env.lower() == "production"


@lru_cache
def get_settings() -> Settings:
    """Cached singleton so the environment is parsed exactly once."""
    return Settings()
