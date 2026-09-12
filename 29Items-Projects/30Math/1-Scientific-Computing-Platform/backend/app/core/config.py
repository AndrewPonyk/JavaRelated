"""Runtime configuration — the single source of truth for every setting.

Precedence: process env > .env file (local only) > defaults below.
Names map 1:1 (case-insensitively) to the variables in /.env.example; keep the
two files in sync. In AWS, secrets arrive as env vars injected from Secrets
Manager / SSM (`/scp/{env}/…`) by the ECS task definition.
"""

from __future__ import annotations

from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # --- Runtime ---
    app_name: str = "Scientific Computing Platform API"
    environment: str = Field(default="local", description="local|dev|staging|prod|test")
    debug: bool = False
    log_level: str = "INFO"
    api_v1_prefix: str = "/api/v1"

    # --- Data stores ---
    database_url: str = "postgresql+psycopg://scp:scp@localhost:5432/scp"
    redis_url: str = "redis://localhost:6379/0"

    # --- Auth ---
    jwt_secret_key: str = "change-me-in-anything-but-local-0123456789"  # never log Settings!
    jwt_algorithm: str = "HS256"
    access_token_ttl_seconds: int = 900
    refresh_token_ttl_seconds: int = 60 * 60 * 24 * 14

    # --- CORS (JSON list in env, e.g. CORS_ORIGINS=["http://localhost:5173"]) ---
    cors_origins: list[str] = ["http://localhost:5173"]

    # --- AWS / ML ---
    aws_region: str = "us-east-1"
    s3_artifacts_bucket: str = ""  # empty ⇒ local artifact store (artifacts_dir)
    sagemaker_recognizer_endpoint: str = ""  # empty ⇒ heuristic fallback (fail-soft)

    # --- Artifacts (plots and other computation outputs) ---
    artifacts_dir: str = "./artifacts"  # shared api↔worker volume in docker-compose

    # --- Compute limits & caching ---
    sync_solve_timeout_seconds: float = 2.0  # API fast-path budget before 202/504
    worker_op_timeout_seconds: float = 30.0  # sandbox budget for queued jobs
    max_expression_length: int = 512
    solve_cache_ttl_seconds: int = 3600

    # --- Rate limiting (0 disables; per identity per minute on POST /api/v1/*) ---
    rate_limit_per_minute: int = 120

    # --- Celery ---
    celery_task_always_eager: bool = False  # true in tests: tasks run inline


@lru_cache
def get_settings() -> Settings:
    return Settings()
