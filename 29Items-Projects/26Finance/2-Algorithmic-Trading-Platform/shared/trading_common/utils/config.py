"""Single config entry point.

Precedence (low → high): built-in defaults → config/<env>/config.yaml → environment.
All services read config through ``load_settings`` only — no scattered ``os.getenv``
(TECH-NOTES §3.4). Secrets come from the environment (injected by Vault in prod).
"""

from __future__ import annotations

import os
from functools import lru_cache
from pathlib import Path
from typing import Any

import yaml
from pydantic import BaseModel, Field


class Settings(BaseModel):
    env: str = Field(default="dev")
    service_name: str = Field(default="unknown")
    log_level: str = Field(default="INFO")

    database_url: str = Field(default="postgresql://trader:changeme@localhost:5432/trading")
    redis_url: str = Field(default="redis://localhost:6379/0")
    kafka_bootstrap_servers: str = Field(default="localhost:9092")
    kafka_consumer_group: str = Field(default="default")

    # Free-form bag for service-specific keys loaded from YAML.
    extra: dict[str, Any] = Field(default_factory=dict)


def _load_yaml(env: str) -> dict[str, Any]:
    path = Path(__file__).resolve().parents[3] / "config" / env / "config.yaml"
    if not path.exists():
        return {}
    with path.open("r", encoding="utf-8") as fh:
        return yaml.safe_load(fh) or {}


@lru_cache(maxsize=1)
def load_settings(service_name: str | None = None) -> Settings:
    """Build Settings from YAML overlaid with environment variables (env wins)."""
    env = os.getenv("ENV", "dev")
    data: dict[str, Any] = _load_yaml(env)

    data.update(
        env=env,
        service_name=service_name or os.getenv("SERVICE_NAME", data.get("service_name", "unknown")),
        log_level=os.getenv("LOG_LEVEL", data.get("log_level", "INFO")),
        database_url=os.getenv("DATABASE_URL", data.get("database_url", Settings().database_url)),
        redis_url=os.getenv("REDIS_URL", data.get("redis_url", Settings().redis_url)),
        kafka_bootstrap_servers=os.getenv(
            "KAFKA_BOOTSTRAP_SERVERS", data.get("kafka_bootstrap_servers", "localhost:9092")
        ),
        kafka_consumer_group=os.getenv(
            "KAFKA_CONSUMER_GROUP", data.get("kafka_consumer_group", "default")
        ),
    )
    return Settings(**data)
