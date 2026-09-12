import pytest
from pydantic import ValidationError

from app.core.config import Settings


def test_auth_required_rejects_weak_secret() -> None:
    with pytest.raises(ValidationError):
        Settings(auth_required=True, jwt_secret="short")


def test_auth_required_accepts_strong_secret() -> None:
    settings = Settings(auth_required=True, jwt_secret="x" * 32)

    assert settings.auth_required is True
