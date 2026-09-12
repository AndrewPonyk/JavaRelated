"""Production must refuse to boot with placeholder/dev secrets.

A `Settings` built with `APP_ENV=production` and a default `JWT_SECRET_KEY`
would sign tokens with a publicly-known key (auth bypass), so construction must
raise. Development keeps the convenient defaults.
"""

from __future__ import annotations

import pytest
from app.core.config import Settings

_REAL = {
    "postgres_password": "a-real-db-password",
    "s3_access_key": "AKIAREALKEY",
    "s3_secret_key": "a-real-object-store-secret",
    "database_url_override": "sqlite+aiosqlite://",
}


def test_prod_rejects_default_jwt_secret() -> None:
    with pytest.raises(ValueError, match="insecure default secrets"):
        Settings(app_env="production", jwt_secret_key="change-me", **_REAL)


def test_prod_rejects_default_object_store_creds() -> None:
    with pytest.raises(ValueError, match="S3_SECRET_KEY"):
        Settings(
            app_env="production",
            jwt_secret_key="a-long-random-production-secret",
            postgres_password="a-real-db-password",
            s3_access_key="minioadmin",
            s3_secret_key="minioadmin",
            database_url_override="sqlite+aiosqlite://",
        )


def test_prod_allows_real_secrets() -> None:
    settings = Settings(
        app_env="production",
        jwt_secret_key="a-long-random-production-secret",
        **_REAL,
    )
    assert settings.is_production


def test_development_allows_defaults() -> None:
    # The placeholder defaults must not blow up outside production.
    settings = Settings(app_env="development", jwt_secret_key="change-me")
    assert not settings.is_production
