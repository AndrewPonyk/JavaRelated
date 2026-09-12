"""Alert-rule service + repository implementations.

The repository protocol decouples the API from storage: dev/tests use the in-memory
implementation; production uses OpenSearch (`la-alert-rules` index, migration 0006).
All methods are async — the OpenSearch repository does real I/O and must never block
the event loop.
"""

from __future__ import annotations

from dataclasses import dataclass, field, replace
from datetime import datetime, timezone
from typing import Any, Protocol
from urllib.parse import quote
from uuid import uuid4

import httpx

from log_analytics.api.schemas import AlertRuleCreate, AlertRuleRead, AlertRuleUpdate
from log_analytics.common.models import AlertSeverity
from log_analytics.common.opensearch import OpenSearchError, async_client, request_json

RULES_INDEX = "la-alert-rules"


class RuleNotFoundError(Exception):
    def __init__(self, rule_id: str) -> None:
        super().__init__(f"alert rule not found: {rule_id}")
        self.rule_id = rule_id


class DuplicateRuleNameError(Exception):
    def __init__(self, name: str) -> None:
        super().__init__(f"alert rule name already exists: {name}")


@dataclass(frozen=True)
class StoredRule:
    """Storage-shape of a rule (independent from API schemas on purpose)."""

    id: str
    name: str
    description: str
    metric: str
    op: str
    threshold: float
    window: str
    severity: AlertSeverity
    channels: list[str] = field(default_factory=list)
    enabled: bool = True
    created_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))
    updated_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))


class AlertRuleRepository(Protocol):
    async def create(self, rule: StoredRule) -> StoredRule: ...
    async def get(self, rule_id: str) -> StoredRule | None: ...
    async def list(
        self, *, limit: int, offset: int, enabled: bool | None
    ) -> tuple[list[StoredRule], int]: ...
    async def save(self, rule: StoredRule) -> StoredRule: ...
    async def delete(self, rule_id: str) -> bool: ...
    async def find_by_name(self, name: str) -> StoredRule | None: ...


class InMemoryAlertRuleRepository:
    """Dev/test repository. Not for production (state dies with the process)."""

    def __init__(self) -> None:
        self._rules: dict[str, StoredRule] = {}

    async def create(self, rule: StoredRule) -> StoredRule:
        self._rules[rule.id] = rule
        return rule

    async def get(self, rule_id: str) -> StoredRule | None:
        return self._rules.get(rule_id)

    async def list(
        self, *, limit: int, offset: int, enabled: bool | None
    ) -> tuple[list[StoredRule], int]:
        rules = sorted(self._rules.values(), key=lambda r: r.created_at)
        if enabled is not None:
            rules = [r for r in rules if r.enabled == enabled]
        return rules[offset : offset + limit], len(rules)

    async def save(self, rule: StoredRule) -> StoredRule:
        self._rules[rule.id] = rule
        return rule

    async def delete(self, rule_id: str) -> bool:
        return self._rules.pop(rule_id, None) is not None

    async def find_by_name(self, name: str) -> StoredRule | None:
        return next((r for r in self._rules.values() if r.name == name), None)


class OpenSearchAlertRuleRepository:
    """Production repository over the `la-alert-rules` index (strict mapping, doc _id = rule id).

    Writes use `refresh=wait_for` so a create is immediately visible to the next list call —
    rule management is low-volume admin traffic, consistency beats indexing throughput here.
    """

    def __init__(
        self,
        base_url: str,
        auth: tuple[str, str] | None = None,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._client = async_client(base_url, auth=auth, transport=transport)

    @staticmethod
    def _doc_path(rule_id: str) -> str:
        # Percent-encode the id so it can only ever be a document id, never extra path
        # segments or query params (the router validates too — this is defense in depth).
        return f"/{RULES_INDEX}/_doc/{quote(rule_id, safe='')}"

    async def create(self, rule: StoredRule) -> StoredRule:
        # op_type=create → 409 if the id somehow exists, instead of silent overwrite.
        await request_json(
            self._client,
            "PUT",
            f"{self._doc_path(rule.id)}?refresh=wait_for&op_type=create",
            json=_to_doc(rule),
        )
        return rule

    async def get(self, rule_id: str) -> StoredRule | None:
        try:
            data = await request_json(self._client, "GET", self._doc_path(rule_id))
        except OpenSearchError as exc:
            if exc.status_code == 404:
                return None
            raise
        return _from_doc(data["_id"], data["_source"])

    async def list(
        self, *, limit: int, offset: int, enabled: bool | None
    ) -> tuple[list[StoredRule], int]:
        query: dict[str, Any] = (
            {"term": {"enabled": enabled}} if enabled is not None else {"match_all": {}}
        )
        data = await request_json(
            self._client,
            "POST",
            f"/{RULES_INDEX}/_search",
            json={
                "query": query,
                "sort": [{"created_at": {"order": "asc"}}, {"_id": {"order": "asc"}}],
                "from": offset,
                "size": limit,
                "track_total_hits": True,
            },
        )
        hits = data["hits"]
        rules = [_from_doc(h["_id"], h["_source"]) for h in hits["hits"]]
        return rules, hits["total"]["value"]

    async def save(self, rule: StoredRule) -> StoredRule:
        await request_json(
            self._client,
            "PUT",
            f"{self._doc_path(rule.id)}?refresh=wait_for",
            json=_to_doc(rule),
        )
        return rule

    async def delete(self, rule_id: str) -> bool:
        try:
            await request_json(
                self._client, "DELETE", f"{self._doc_path(rule_id)}?refresh=wait_for"
            )
        except OpenSearchError as exc:
            if exc.status_code == 404:
                return False
            raise
        return True

    async def find_by_name(self, name: str) -> StoredRule | None:
        data = await request_json(
            self._client,
            "POST",
            f"/{RULES_INDEX}/_search",
            json={"query": {"term": {"name": name}}, "size": 1},
        )
        hits = data["hits"]["hits"]
        return _from_doc(hits[0]["_id"], hits[0]["_source"]) if hits else None

    async def aclose(self) -> None:
        await self._client.aclose()


def _to_doc(rule: StoredRule) -> dict[str, Any]:
    """Index document — matches the strict mapping in migration 0006 (id lives in _id)."""
    return {
        "name": rule.name,
        "description": rule.description,
        "metric": rule.metric,
        "op": rule.op,
        "threshold": rule.threshold,
        "window": rule.window,
        "severity": rule.severity.value,
        "channels": rule.channels,
        "enabled": rule.enabled,
        "created_at": rule.created_at.isoformat(),
        "updated_at": rule.updated_at.isoformat(),
    }


def _from_doc(doc_id: str, source: dict[str, Any]) -> StoredRule:
    return StoredRule(
        id=doc_id,
        name=source["name"],
        description=source.get("description", ""),
        metric=source["metric"],
        op=source["op"],
        threshold=float(source["threshold"]),
        window=source["window"],
        severity=AlertSeverity(source["severity"]),
        channels=list(source.get("channels", [])),
        enabled=bool(source.get("enabled", True)),
        created_at=datetime.fromisoformat(source["created_at"]),
        updated_at=datetime.fromisoformat(source["updated_at"]),
    )


class AlertRuleService:
    """Behavior: uniqueness, timestamps, partial updates. Storage-agnostic."""

    def __init__(self, repository: AlertRuleRepository) -> None:
        self._repo = repository

    async def create(self, payload: AlertRuleCreate) -> AlertRuleRead:
        if await self._repo.find_by_name(payload.name):
            raise DuplicateRuleNameError(payload.name)
        now = datetime.now(timezone.utc)
        rule = StoredRule(id=str(uuid4()), created_at=now, updated_at=now, **payload.model_dump())
        return _to_read(await self._repo.create(rule))

    async def get(self, rule_id: str) -> AlertRuleRead:
        rule = await self._repo.get(rule_id)
        if rule is None:
            raise RuleNotFoundError(rule_id)
        return _to_read(rule)

    async def list(
        self, *, limit: int = 50, offset: int = 0, enabled: bool | None = None
    ) -> tuple[list[AlertRuleRead], int]:
        rules, total = await self._repo.list(limit=limit, offset=offset, enabled=enabled)
        return [_to_read(r) for r in rules], total

    async def update(self, rule_id: str, payload: AlertRuleUpdate) -> AlertRuleRead:
        rule = await self._repo.get(rule_id)
        if rule is None:
            raise RuleNotFoundError(rule_id)
        changes = payload.model_dump(exclude_unset=True, exclude_none=True)
        if "name" in changes and changes["name"] != rule.name:
            existing = await self._repo.find_by_name(changes["name"])
            if existing is not None and existing.id != rule_id:
                raise DuplicateRuleNameError(changes["name"])
        updated = replace(rule, updated_at=datetime.now(timezone.utc), **changes)
        return _to_read(await self._repo.save(updated))

    async def delete(self, rule_id: str) -> None:
        if not await self._repo.delete(rule_id):
            raise RuleNotFoundError(rule_id)


def _to_read(rule: StoredRule) -> AlertRuleRead:
    return AlertRuleRead.model_validate(rule)
