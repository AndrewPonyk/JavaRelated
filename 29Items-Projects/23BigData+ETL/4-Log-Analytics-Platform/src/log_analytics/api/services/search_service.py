"""Search service: builds constrained OpenSearch queries and executes them over REST.

Plain REST via httpx keeps the service dependency-light; swap to opensearch-py + SigV4
(`OpenSearchAsyncHttpConnection` + boto session) when pointing at AWS domains.
"""

from __future__ import annotations

from datetime import datetime
from typing import Any

import httpx

from log_analytics.api.schemas import LogSearchResponse

LOGS_ALIAS = "la-logs"


class SearchBackendError(Exception):
    pass


class SearchService:
    def __init__(
        self,
        base_url: str,
        auth: tuple[str, str] | None = None,
        timeout: float = 10.0,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._auth = auth
        self._timeout = timeout
        self._transport = transport  # test hook (httpx.MockTransport)

    async def search_logs(
        self,
        *,
        query: str | None,
        service: str | None,
        level: str | None,
        from_ts: datetime | None,
        to_ts: datetime | None,
        size: int,
        offset: int,
    ) -> LogSearchResponse:
        body = self._build_query(
            query=query,
            service=service,
            level=level,
            from_ts=from_ts,
            to_ts=to_ts,
            size=size,
            offset=offset,
        )
        try:
            async with httpx.AsyncClient(
                timeout=self._timeout, auth=self._auth, transport=self._transport
            ) as client:
                resp = await client.post(f"{self._base_url}/{LOGS_ALIAS}/_search", json=body)
        except httpx.HTTPError as exc:
            raise SearchBackendError(str(exc)) from exc
        if resp.status_code != 200:
            raise SearchBackendError(f"HTTP {resp.status_code}: {resp.text[:300]}")

        data = resp.json()
        hits = data.get("hits", {})
        return LogSearchResponse(
            total=hits.get("total", {}).get("value", 0),
            took_ms=data.get("took", 0),
            hits=[h.get("_source", {}) for h in hits.get("hits", [])],
        )

    @staticmethod
    def _build_query(
        *,
        query: str | None,
        service: str | None,
        level: str | None,
        from_ts: datetime | None,
        to_ts: datetime | None,
        size: int,
        offset: int,
    ) -> dict[str, Any]:
        must: list[dict[str, Any]] = []
        filters: list[dict[str, Any]] = []

        if query:
            must.append({"match": {"message": {"query": query, "operator": "and"}}})
        if service:
            filters.append({"term": {"service": service}})
        if level:
            filters.append({"term": {"level": level}})
        time_range: dict[str, str] = {}
        if from_ts:
            time_range["gte"] = from_ts.isoformat()
        if to_ts:
            time_range["lte"] = to_ts.isoformat()
        if time_range:
            filters.append({"range": {"@timestamp": time_range}})

        return {
            "query": {"bool": {"must": must or [{"match_all": {}}], "filter": filters}},
            "sort": [{"@timestamp": {"order": "desc"}}],
            "size": size,
            "from": offset,
            "track_total_hits": True,
        }
