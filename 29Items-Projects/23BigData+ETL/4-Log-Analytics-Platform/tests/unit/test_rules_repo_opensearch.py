"""OpenSearch alert-rule repository against a stateful in-memory cluster fake.

Exercises the exact request/response shapes the repository sends — the same CRUD paths
the integration environment hits, minus the network.
"""

from __future__ import annotations

import asyncio
import json
import re

import httpx
import pytest

from log_analytics.api.schemas import AlertRuleCreate, AlertRuleUpdate
from log_analytics.api.services.alert_rule_service import (
    AlertRuleService,
    DuplicateRuleNameError,
    OpenSearchAlertRuleRepository,
    RuleNotFoundError,
)

_DOC_PATH = re.compile(r"^/la-alert-rules/_doc/(?P<id>[^/?]+)$")


class FakeCluster:
    """Just enough of the OpenSearch document API for the repository."""

    def __init__(self) -> None:
        self.docs: dict[str, dict] = {}

    def handler(self, request: httpx.Request) -> httpx.Response:
        path, method = request.url.path, request.method
        if path == "/la-alert-rules/_search" and method == "POST":
            return self._search(json.loads(request.content))
        match = _DOC_PATH.match(path)
        if not match:
            return httpx.Response(400, json={"error": f"unexpected path {path}"})
        doc_id = match.group("id")
        if method == "PUT":
            if "op_type=create" in str(request.url.query) and doc_id in self.docs:
                return httpx.Response(409, json={"error": "version_conflict"})
            self.docs[doc_id] = json.loads(request.content)
            return httpx.Response(201, json={"result": "created", "_id": doc_id})
        if method == "GET":
            if doc_id not in self.docs:
                return httpx.Response(404, json={"found": False})
            return httpx.Response(200, json={"_id": doc_id, "_source": self.docs[doc_id]})
        if method == "DELETE":
            if doc_id not in self.docs:
                return httpx.Response(404, json={"result": "not_found"})
            del self.docs[doc_id]
            return httpx.Response(200, json={"result": "deleted"})
        return httpx.Response(405, json={})

    def _search(self, body: dict) -> httpx.Response:
        query = body.get("query", {"match_all": {}})
        items = list(self.docs.items())
        if "term" in query:
            field, value = next(iter(query["term"].items()))
            items = [(i, d) for i, d in items if d.get(field) == value]
        items.sort(key=lambda pair: pair[1]["created_at"])
        total = len(items)
        offset, size = body.get("from", 0), body.get("size", 10)
        hits = [{"_id": i, "_source": d} for i, d in items[offset : offset + size]]
        return httpx.Response(200, json={"hits": {"total": {"value": total}, "hits": hits}})


@pytest.fixture()
def service() -> AlertRuleService:
    cluster = FakeCluster()
    repo = OpenSearchAlertRuleRepository(
        "http://opensearch:9200", transport=httpx.MockTransport(cluster.handler)
    )
    return AlertRuleService(repo)


def _payload(name: str = "High error ratio") -> AlertRuleCreate:
    return AlertRuleCreate(
        name=name,
        metric="error_ratio",
        op="gt",
        threshold=0.05,
        window="5m",
        channels=["log", "slack"],
    )


def test_crud_roundtrip(service: AlertRuleService) -> None:
    async def scenario() -> None:
        created = await service.create(_payload())
        fetched = await service.get(created.id)
        assert fetched.name == "High error ratio"
        assert fetched.severity.value == "warning"

        updated = await service.update(created.id, AlertRuleUpdate(threshold=0.2, enabled=False))
        assert updated.threshold == 0.2
        assert updated.enabled is False
        assert updated.updated_at >= created.updated_at

        items, total = await service.list()
        assert total == 1 and items[0].id == created.id

        await service.delete(created.id)
        with pytest.raises(RuleNotFoundError):
            await service.get(created.id)

    asyncio.run(scenario())


def test_duplicate_names_rejected(service: AlertRuleService) -> None:
    async def scenario() -> None:
        await service.create(_payload("same-name"))
        with pytest.raises(DuplicateRuleNameError):
            await service.create(_payload("same-name"))

    asyncio.run(scenario())


def test_list_pagination_and_enabled_filter(service: AlertRuleService) -> None:
    async def scenario() -> None:
        for i in range(5):
            created = await service.create(_payload(f"rule number {i}"))
            if i % 2 == 0:
                await service.update(created.id, AlertRuleUpdate(enabled=False))
        page, total = await service.list(limit=2, offset=2)
        assert total == 5 and len(page) == 2
        enabled_only, enabled_total = await service.list(enabled=True)
        assert enabled_total == 2
        assert all(r.enabled for r in enabled_only)

    asyncio.run(scenario())


def test_severity_survives_storage_roundtrip(service: AlertRuleService) -> None:
    async def scenario() -> None:
        payload = AlertRuleCreate(
            name="critical burst",
            metric="error_count",
            op="gte",
            threshold=100,
            severity="critical",
            channels=["pagerduty"],
        )
        created = await service.create(payload)
        fetched = await service.get(created.id)
        assert fetched.severity.value == "critical"
        assert fetched.channels == ["pagerduty"]

    asyncio.run(scenario())


def test_doc_ids_are_percent_encoded_on_the_wire() -> None:
    """A hostile id must reach the cluster as one opaque document id — never as extra
    path segments or query parameters (the router blocks these too; belt and braces)."""
    captured: list[bytes] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request.url.raw_path)
        return httpx.Response(404, json={"found": False})

    repo = OpenSearchAlertRuleRepository(
        "http://opensearch:9200", transport=httpx.MockTransport(handler)
    )
    assert asyncio.run(repo.get("weird/../id?x=1")) is None
    assert captured == [b"/la-alert-rules/_doc/weird%2F..%2Fid%3Fx%3D1"]
