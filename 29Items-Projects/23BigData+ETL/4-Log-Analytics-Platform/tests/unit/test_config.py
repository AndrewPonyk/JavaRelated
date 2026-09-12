"""Settings behavior: env parsing, derived properties. `_env_file=None` isolates from .env."""

from __future__ import annotations

from log_analytics.common.config import Settings


def _settings(**overrides) -> Settings:
    return Settings(_env_file=None, **overrides)


def test_defaults_are_local_dev() -> None:
    settings = _settings()
    assert settings.app_env == "dev"
    assert settings.kafka_bootstrap_servers == "localhost:29092"
    assert settings.rules_backend == "auto"
    assert settings.gateway_rate_limit_rps == 0.0


def test_env_prefix_and_types(monkeypatch) -> None:
    monkeypatch.setenv("LA_GATEWAY_MAX_BATCH", "42")
    monkeypatch.setenv("LA_ANOMALY_ALERT_THRESHOLD", "0.9")
    settings = _settings()
    assert settings.gateway_max_batch == 42
    assert settings.anomaly_alert_threshold == 0.9


def test_api_key_set_parses_and_strips() -> None:
    settings = _settings(gateway_api_keys="alpha, beta ,,gamma")
    assert settings.gateway_api_key_set == frozenset({"alpha", "beta", "gamma"})
    assert _settings(gateway_api_keys="").gateway_api_key_set == frozenset()


def test_opensearch_auth_only_when_username_present() -> None:
    assert _settings().opensearch_auth is None
    settings = _settings(opensearch_username="admin", opensearch_password="pw")
    assert settings.opensearch_auth == ("admin", "pw")


def test_cors_origin_list() -> None:
    assert _settings().cors_origin_list == []
    settings = _settings(cors_origins="https://a.example, https://b.example")
    assert settings.cors_origin_list == ["https://a.example", "https://b.example"]
