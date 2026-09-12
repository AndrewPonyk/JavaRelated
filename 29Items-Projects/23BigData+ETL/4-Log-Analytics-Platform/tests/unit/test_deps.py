"""Rule-storage backend selection (LA_RULES_BACKEND: memory | opensearch | auto)."""

from __future__ import annotations

import asyncio

import pytest

import log_analytics.api.deps as deps
from log_analytics.api.services.alert_rule_service import (
    InMemoryAlertRuleRepository,
    OpenSearchAlertRuleRepository,
)
from log_analytics.common.config import Settings


@pytest.fixture(autouse=True)
def clean_singleton():
    deps.reset_rule_service()
    yield
    deps.reset_rule_service()


def _select_backend(monkeypatch, backend: str, reachable: bool = False):
    settings = Settings(_env_file=None, rules_backend=backend)
    monkeypatch.setattr(deps, "get_settings", lambda: settings)

    async def probe(_settings) -> bool:
        return reachable

    monkeypatch.setattr(deps, "_opensearch_reachable", probe)
    return asyncio.run(deps.get_alert_rule_service())


def test_memory_backend(monkeypatch) -> None:
    service = _select_backend(monkeypatch, "memory")
    assert isinstance(service._repo, InMemoryAlertRuleRepository)


def test_opensearch_backend(monkeypatch) -> None:
    service = _select_backend(monkeypatch, "opensearch")
    assert isinstance(service._repo, OpenSearchAlertRuleRepository)


def test_auto_prefers_opensearch_when_reachable(monkeypatch) -> None:
    service = _select_backend(monkeypatch, "auto", reachable=True)
    assert isinstance(service._repo, OpenSearchAlertRuleRepository)


def test_auto_falls_back_to_memory(monkeypatch) -> None:
    service = _select_backend(monkeypatch, "auto", reachable=False)
    assert isinstance(service._repo, InMemoryAlertRuleRepository)


def test_unknown_backend_rejected(monkeypatch) -> None:
    with pytest.raises(ValueError, match="unknown LA_RULES_BACKEND"):
        _select_backend(monkeypatch, "banana")


def test_service_is_cached_singleton(monkeypatch) -> None:
    first = _select_backend(monkeypatch, "memory")
    second = asyncio.run(deps.get_alert_rule_service())
    assert first is second
