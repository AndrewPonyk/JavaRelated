"""Unit tests for settings validation (fail-fast on insecure prod config)."""
from __future__ import annotations

import pytest
from pydantic import ValidationError

from src.core.config import Settings


def test_dev_fills_insecure_default_secret() -> None:
    s = Settings(app_env="dev", auth_mode="hs256", jwt_secret="")
    assert s.jwt_secret  # a non-empty dev key is injected


def test_prod_disabled_auth_rejected() -> None:
    with pytest.raises(ValidationError):
        Settings(app_env="prod", auth_mode="disabled")


def test_prod_hs256_without_secret_rejected() -> None:
    with pytest.raises(ValidationError):
        Settings(app_env="prod", auth_mode="hs256", jwt_secret="")


def test_prod_wildcard_cors_rejected() -> None:
    with pytest.raises(ValidationError):
        Settings(
            app_env="prod",
            auth_mode="hs256",
            jwt_secret="strong",
            cors_allow_origins=["*"],
        )


def test_prod_valid_config_ok() -> None:
    s = Settings(
        app_env="prod",
        auth_mode="hs256",
        jwt_secret="strong-secret",
        cors_allow_origins=["https://app.example.com"],
    )
    assert s.is_production
