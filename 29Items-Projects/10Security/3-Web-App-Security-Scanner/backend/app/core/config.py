"""Environment-driven application settings (12-factor).

All runtime configuration flows through this class — no `os.environ` reads
scattered through the codebase. `.env` for local dev; injected secrets in CI/prod.
"""

from __future__ import annotations

from functools import lru_cache

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # ── Core ────────────────────────────────────────────────────────────
    ENV: str = "development"  # development | staging | production
    API_PREFIX: str = "/api/v1"
    SECRET_KEY: str = "insecure-dev-key-change-me"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 30
    REFRESH_TOKEN_EXPIRE_DAYS: int = 7
    CORS_ORIGINS: list[str] = ["http://localhost:5173"]

    # First-run admin bootstrap (seeded only if no admin exists)
    ADMIN_EMAIL: str = "admin@scanner.local"
    ADMIN_PASSWORD: str = "change-me-admin"

    # bucket -> (burst capacity, refill per minute); missing bucket = unlimited
    RATE_LIMITS: dict[str, tuple[int, float]] = {
        "auth": (20, 10.0),
        "scan_launch": (10, 5.0),
    }

    # ── Database ────────────────────────────────────────────────────────
    POSTGRES_HOST: str = "db"
    POSTGRES_PORT: int = 5432
    POSTGRES_DB: str = "secscanner"
    POSTGRES_USER: str = "secscanner"
    POSTGRES_PASSWORD: str = "secscanner"

    # ── Scanners ────────────────────────────────────────────────────────
    ZAP_BASE_URL: str = "http://zap:8080"
    ZAP_API_KEY: str = ""
    SQLMAP_BASE_URL: str = "http://sqlmap:8775"
    SQLMAP_USERNAME: str = "sqlmap"
    SQLMAP_PASSWORD: str = "dev-sqlmap-pass"
    SCAN_TIMEOUT_MINUTES: int = 45
    MAX_CONCURRENT_SCANS: int = 3

    # ── Target scoping (SSRF / authorization guardrails) ────────────────
    ALLOWED_TARGET_CIDRS: list[str] = Field(default_factory=list)
    ALLOW_PRIVATE_TARGETS: bool = False

    # ── ML classifier ───────────────────────────────────────────────────
    ML_MODEL_PATH: str = "ml/severity_model.joblib"
    ML_FALLBACK_SEVERITY: str = "medium"

    # ── Notifications & retention ──────────────────────────────────────
    SCAN_WEBHOOK_URL: str = ""  # Slack-compatible JSON webhook ("" = disabled)
    EVIDENCE_RETENTION_DAYS: int = 90  # 0 disables the purge job

    @property
    def database_url(self) -> str:
        return (
            f"postgresql+asyncpg://{self.POSTGRES_USER}:{self.POSTGRES_PASSWORD}"
            f"@{self.POSTGRES_HOST}:{self.POSTGRES_PORT}/{self.POSTGRES_DB}"
        )

    @field_validator("ENV")
    @classmethod
    def _validate_env(cls, v: str) -> str:
        allowed = {"development", "staging", "production"}
        if v not in allowed:
            raise ValueError(f"ENV must be one of {allowed}, got {v!r}")
        return v


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
