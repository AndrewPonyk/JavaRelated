"""Settings: production safety rail + CSV parsing properties."""

import pytest

from app.core.config import Settings


def test_production_refuses_placeholder_secrets():
    with pytest.raises(ValueError, match="JWT_SECRET.*ADMIN_PASSWORD.*DEVICE_API_KEY"):
        Settings(_env_file=None, environment="production")


def test_production_names_only_the_offending_secrets():
    with pytest.raises(ValueError, match="DEVICE_API_KEY") as excinfo:
        Settings(
            _env_file=None,
            environment="production",
            jwt_secret="f" * 64,
            admin_password="real-admin-pass",
        )
    assert "JWT_SECRET" not in str(excinfo.value)


def test_production_boots_with_real_secrets():
    settings = Settings(
        _env_file=None,
        environment="production",
        jwt_secret="f" * 64,
        admin_password="real-admin-pass",
        device_api_key="gw-" + "a" * 32,
    )
    assert settings.environment == "production"


def test_production_allows_empty_admin_password_to_disable_bootstrap():
    settings = Settings(
        _env_file=None,
        environment="production",
        jwt_secret="f" * 64,
        admin_password="",
        device_api_key="gw-" + "a" * 32,
    )
    assert settings.admin_password == ""


def test_development_allows_placeholders():
    assert Settings(_env_file=None).environment == "development"


def test_csv_properties_strip_and_drop_empties():
    settings = Settings(
        _env_file=None,
        cors_origins="http://a, http://b ,",
        cassandra_contact_points=" node1 ,node2",
    )
    assert settings.cors_origins_list == ["http://a", "http://b"]
    assert settings.cassandra_contact_points_list == ["node1", "node2"]
