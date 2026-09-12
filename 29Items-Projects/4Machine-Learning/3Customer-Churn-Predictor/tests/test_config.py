"""Unit tests for configuration loading."""

from __future__ import annotations

from churn_predictor.config import Settings, get_settings


def test_get_settings_is_cached() -> None:
    assert get_settings() is get_settings()


def test_repr_redacts_secrets() -> None:
    s = Settings(database_url="postgresql+psycopg://user:supersecret@host:5432/db")
    text = repr(s)
    assert "supersecret" not in text
    assert "redacted" in text


def test_defaults_present() -> None:
    s = Settings()
    assert s.random_seed == 42
    assert s.cv_folds >= 2
    assert s.optuna_n_trials >= 1
