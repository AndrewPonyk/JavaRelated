"""Runtime configuration.

Single source of truth for all tunables. Values come from (highest precedence first):
process environment > `.env` file > defaults below. Cloud environments inject secrets
via GCP Secret Manager → Cloud Run env vars; the app never talks to Secret Manager itself.
"""

from functools import lru_cache
from typing import Literal

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # --- App ---
    app_name: str = "search-engine-backend"
    environment: Literal["dev", "staging", "prod"] = "dev"
    debug: bool = False
    api_v1_prefix: str = "/api/v1"
    cors_origins: list[str] = ["http://localhost:5173"]

    # --- PostgreSQL (catalog source of truth) ---
    database_url: str = "postgresql+asyncpg://search:search@localhost:5432/search"
    database_pool_size: int = 10

    # --- Elasticsearch (read model) ---
    es_url: str = "http://localhost:9200"
    es_api_key: str | None = None  # required outside dev (Elastic Cloud API key)
    es_products_alias: str = "products"  # queries go through the alias, never a versioned index
    es_request_timeout_s: float = 5.0

    # --- Redis (autocomplete cache) ---
    redis_url: str = "redis://localhost:6379/0"
    suggest_cache_ttl_s: int = 300
    suggest_cache_jitter_s: int = 60  # randomized extra TTL — avoids synchronized expiry
    suggest_min_prefix_len: int = 2

    # --- PG -> ES outbox worker (ARCHITECTURE §2.3) ---
    outbox_enabled: bool = True
    outbox_poll_interval_s: float = 2.0
    outbox_batch_size: int = 200
    outbox_max_attempts: int = 10  # rows beyond this are dead-lettered (logged, skipped)

    # --- Rate limiting (per-instance backstop; Cloud Armor is the real edge limiter) ---
    rate_limit_enabled: bool = True
    rate_limit_requests: int = 60  # allowed requests per window per client IP
    rate_limit_window_s: float = 10.0

    # --- Learning to Rank (see app/search/ltr.py and TECH-NOTES §3.6) ---
    ltr_mode: Literal["off", "plugin", "native"] = "off"
    ltr_model_name: str = "products_ltr_v1"
    ltr_rescore_window: int = 100  # LTR scores only the top-N BM25 hits

    # --- Security ---
    admin_api_key: str = Field(default="change-me", description="X-API-Key for admin endpoints")

    @model_validator(mode="after")
    def _require_real_secrets_outside_dev(self) -> "Settings":
        """Fail at boot, not at first request, if placeholder secrets leak into
        staging/prod (they come from Secret Manager there — see TECH-NOTES §3.4)."""
        if self.environment != "dev":
            problems = []
            if self.admin_api_key == "change-me":
                problems.append("ADMIN_API_KEY still has the placeholder value")
            if not self.es_api_key:
                problems.append("ES_API_KEY is required outside dev")
            if problems:
                raise ValueError(f"invalid {self.environment} config: " + "; ".join(problems))
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
