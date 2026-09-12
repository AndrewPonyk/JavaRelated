"""Runtime configuration — every knob comes from env vars (.env in dev).

`.env.example` at the repo root is the authoritative list; the unit test
tests/integration/test_config.py::test_config_documented asserts the two
stay in sync.
"""

from __future__ import annotations

from functools import lru_cache
from typing import Annotated, Literal

from pydantic import field_validator
from pydantic_settings import BaseSettings, NoDecode, SettingsConfigDict

# CSV-in-env lists: NoDecode stops pydantic-settings from JSON-decoding the raw
# value so the field validator below can split on commas instead.
CsvList = Annotated[list[str], NoDecode]

ALL_SCOPES = frozenset({"price", "fit", "admin"})

_PERIOD_SECONDS = {"second": 1.0, "minute": 60.0, "hour": 3600.0}


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="QF_", env_file=".env", extra="ignore")

    env: Literal["dev", "staging", "prod"] = "dev"
    log_level: str = "INFO"
    require_native: bool = False

    host: str = "0.0.0.0"
    port: int = 8000
    cors_origins: CsvList = ["http://localhost:8000"]
    rate_limit: str = "120/minute"

    # Entries: "<sha256-hex>" (all scopes) or "<sha256-hex>:scope1|scope2"
    # with scopes from {price, fit, admin}.
    api_key_hashes: CsvList = []

    database_url: str = "sqlite+aiosqlite:///./quantfin_dev.db"

    ml_device: Literal["cpu", "cuda"] = "cpu"
    ml_max_fit_seconds: int = 600

    mc_max_paths: int = 10_000_000
    mc_default_seed: int = 42

    @field_validator("cors_origins", "api_key_hashes", mode="before")
    @classmethod
    def _split_csv(cls, v):
        if isinstance(v, str):
            return [item.strip() for item in v.split(",") if item.strip()]
        return v

    @field_validator("rate_limit")
    @classmethod
    def _check_rate_limit(cls, v: str) -> str:
        count, _, period = v.partition("/")
        if not count.isdigit() or int(count) <= 0 or period not in _PERIOD_SECONDS:
            raise ValueError(
                f"rate_limit must look like '120/minute' (period: second|minute|hour), got {v!r}"
            )
        return v

    @field_validator("api_key_hashes")
    @classmethod
    def _check_key_entries(cls, v: list[str]) -> list[str]:
        for entry in v:
            _, _, scope_part = entry.partition(":")
            scopes = {s for s in scope_part.split("|") if s} if scope_part else set()
            unknown = scopes - ALL_SCOPES
            if unknown:
                raise ValueError(
                    f"unknown API-key scopes {sorted(unknown)}; " f"valid: {sorted(ALL_SCOPES)}"
                )
        return v

    def api_keys(self) -> dict[str, frozenset[str]]:
        """Parsed key table: sha256-hex digest -> granted scopes."""
        table: dict[str, frozenset[str]] = {}
        for entry in self.api_key_hashes:
            digest, _, scope_part = entry.partition(":")
            scopes = frozenset(s for s in scope_part.split("|") if s) if scope_part else ALL_SCOPES
            table[digest.lower()] = scopes
        return table

    def rate_limit_parsed(self) -> tuple[int, float]:
        """(max calls, window seconds)."""
        count, _, period = self.rate_limit.partition("/")
        return int(count), _PERIOD_SECONDS[period]


@lru_cache
def get_settings() -> Settings:
    return Settings()
