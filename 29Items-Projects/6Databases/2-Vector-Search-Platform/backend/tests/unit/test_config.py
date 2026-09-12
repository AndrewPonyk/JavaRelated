"""Unit tests for Settings validation + helpers."""

from __future__ import annotations

import pytest
from app.core.config import Settings
from pydantic import ValidationError


def _settings(**overrides: object) -> Settings:
    base: dict[str, object] = {"app_env": "development", "api_key": "strong-key"}
    base.update(overrides)
    return Settings(**base)  # type: ignore[arg-type]


def test_is_sqlite_true() -> None:
    assert _settings(database_url="sqlite+aiosqlite:///./x.db").is_sqlite


def test_is_sqlite_false_for_postgres() -> None:
    assert not _settings(database_url="postgresql+asyncpg://u:p@h/db").is_sqlite


def test_cors_origins_from_csv_string() -> None:
    s = _settings(cors_origins="http://a.com, http://b.com")
    assert s.cors_origins == ["http://a.com", "http://b.com"]


def test_cors_origins_list_passthrough() -> None:
    s = _settings(cors_origins=["http://x.com"])
    assert s.cors_origins == ["http://x.com"]


def test_production_rejects_default_api_key() -> None:
    with pytest.raises(ValidationError):
        _settings(app_env="production", api_key="dev-local-key-change-me")


def test_production_rejects_blank_api_key() -> None:
    with pytest.raises(ValidationError):
        _settings(app_env="production", api_key="")


def test_production_accepts_strong_api_key() -> None:
    s = _settings(app_env="production", api_key="a-genuinely-strong-secret")
    assert s.is_production
