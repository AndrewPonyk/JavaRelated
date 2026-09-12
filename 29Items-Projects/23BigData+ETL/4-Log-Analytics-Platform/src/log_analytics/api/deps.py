"""Dependency-injection wiring for the API.

Rule storage backend is chosen once per process from LA_RULES_BACKEND:
  opensearch → la-alert-rules index (production)
  memory     → in-process dict (unit tests, quick demos)
  auto       → probe OpenSearch (1.5 s); fall back to memory with a warning (local dev)

Tests override `get_alert_rule_service` via `app.dependency_overrides` and never probe.
"""

from __future__ import annotations

import logging
from typing import Annotated

import httpx
from fastapi import Depends

from log_analytics.api.services.alert_rule_service import (
    AlertRuleService,
    InMemoryAlertRuleRepository,
    OpenSearchAlertRuleRepository,
)
from log_analytics.api.services.search_service import SearchService
from log_analytics.common.config import Settings, get_settings

logger = logging.getLogger(__name__)

_rule_service: AlertRuleService | None = None


async def _opensearch_reachable(settings: Settings) -> bool:
    try:
        async with httpx.AsyncClient(timeout=1.5, auth=settings.opensearch_auth) as client:
            resp = await client.get(f"{settings.opensearch_url}/_cluster/health")
            return resp.status_code == 200
    except httpx.HTTPError:
        return False


async def get_alert_rule_service() -> AlertRuleService:
    global _rule_service
    if _rule_service is None:
        settings = get_settings()
        backend = settings.rules_backend
        if backend == "auto":
            backend = "opensearch" if await _opensearch_reachable(settings) else "memory"
            if backend == "memory":
                logger.warning("OpenSearch unreachable — alert rules stored in memory only")
        if backend == "opensearch":
            repo: object = OpenSearchAlertRuleRepository(
                settings.opensearch_url, auth=settings.opensearch_auth
            )
        elif backend == "memory":
            repo = InMemoryAlertRuleRepository()
        else:
            raise ValueError(f"unknown LA_RULES_BACKEND {settings.rules_backend!r}")
        logger.info("alert-rule storage backend: %s", backend)
        _rule_service = AlertRuleService(repo)  # type: ignore[arg-type]
    return _rule_service


def reset_rule_service() -> None:
    """Test hook: drop the cached backend choice."""
    global _rule_service
    _rule_service = None


def get_search_service(settings: Annotated[Settings, Depends(get_settings)]) -> SearchService:
    return SearchService(
        base_url=settings.opensearch_url,
        auth=settings.opensearch_auth,
    )
