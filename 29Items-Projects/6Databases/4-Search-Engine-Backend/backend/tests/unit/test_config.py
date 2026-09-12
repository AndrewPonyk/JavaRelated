"""Settings invariants: placeholder secrets must never boot outside dev."""

import pytest
from pydantic import ValidationError

from app.core.config import Settings


def test_dev_boots_with_placeholder_secrets() -> None:
    settings = Settings(environment="dev")
    assert settings.admin_api_key == "change-me"


def test_staging_rejects_placeholder_admin_key() -> None:
    with pytest.raises(ValidationError, match="ADMIN_API_KEY"):
        Settings(environment="staging", es_api_key="real-key")


def test_prod_requires_es_api_key() -> None:
    with pytest.raises(ValidationError, match="ES_API_KEY"):
        Settings(environment="prod", admin_api_key="real-admin-key")


def test_prod_boots_with_real_secrets() -> None:
    settings = Settings(
        environment="prod", admin_api_key="real-admin-key", es_api_key="real-es-key"
    )
    assert settings.environment == "prod"
