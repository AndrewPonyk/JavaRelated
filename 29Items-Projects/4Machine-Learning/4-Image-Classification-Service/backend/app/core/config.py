"""Application configuration (12-factor, env-driven).

All configuration is read from environment variables exactly once, here.
Nothing else in the codebase should read ``os.environ`` directly.
"""

from __future__ import annotations

from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Typed application settings loaded from the environment / ``.env``."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # --- App ---
    app_env: str = Field(default="development")  # development | staging | production
    log_level: str = Field(default="INFO")
    port: int = Field(default=8080)

    # --- Model ---
    model_version: str = Field(default="v1")
    model_path: str = Field(default="/models/vit_clip_v1.onnx")
    labels_path: str = Field(default="/models/labels.json")
    thresholds_path: str = Field(default="/models/thresholds.json")
    max_upload_bytes: int = Field(default=10 * 1024 * 1024)  # 10 MiB

    # --- Redis (optional; cache degrades gracefully) ---
    redis_url: str | None = Field(default=None)
    cache_ttl_seconds: int = Field(default=86_400)

    # --- Database (taxonomy) ---
    database_url: str = Field(default="postgresql+psycopg://postgres:postgres@localhost:5432/ics")

    # --- Security ---
    api_keys: str = Field(default="")  # comma-separated
    jwt_secret: str = Field(default="change-me-in-prod")
    jwt_algorithm: str = Field(default="HS256")
    jwt_expires_seconds: int = Field(default=3600)
    # Demo console credentials for POST /auth/token (replace with a real IdP in prod).
    console_username: str = Field(default="admin")
    console_password: str = Field(default="admin")

    # --- Rate limiting (per API key / IP, per instance) ---
    rate_limit_enabled: bool = Field(default=True)
    rate_limit_capacity: int = Field(default=120)
    rate_limit_refill_per_sec: float = Field(default=2.0)

    @property
    def is_production(self) -> bool:
        return self.app_env.lower() == "production"

    @property
    def allowed_api_keys(self) -> set[str]:
        return {k.strip() for k in self.api_keys.split(",") if k.strip()}


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Return a cached singleton ``Settings`` instance."""
    return Settings()
