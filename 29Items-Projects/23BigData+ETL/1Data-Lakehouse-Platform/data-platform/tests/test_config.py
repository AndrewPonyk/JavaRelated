"""Fast tests (no Spark) for settings and path conventions."""

from __future__ import annotations

from lakehouse.common.config import LakehouseSettings


def test_defaults_point_at_local_stack(monkeypatch):
    for var in ("LAKE_BRONZE_URI", "KAFKA_BOOTSTRAP_SERVERS", "LAKEHOUSE_LOCAL"):
        monkeypatch.delenv(var, raising=False)
    settings = LakehouseSettings.from_env()
    assert settings.bronze_uri == "s3a://lakehouse-bronze"
    assert settings.kafka_bootstrap_servers == "localhost:9094"
    assert settings.local_mode is False


def test_env_overrides(monkeypatch):
    monkeypatch.setenv("LAKE_SILVER_URI", "s3://prod-lakehouse-silver")
    monkeypatch.setenv("LAKEHOUSE_LOCAL", "1")
    settings = LakehouseSettings.from_env()
    assert settings.silver_uri == "s3://prod-lakehouse-silver"
    assert settings.local_mode is True


def test_path_conventions():
    settings = LakehouseSettings.from_env()
    assert settings.silver_path("sales", "orders").endswith("/sales/orders")
    assert "/_quarantine/sales/orders" in settings.quarantine_path("sales", "orders")
    assert "/_checkpoints/my_job" in settings.checkpoint_path("my_job")
