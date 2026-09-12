"""Search service: query building and response mapping against a mocked cluster."""

from __future__ import annotations

import asyncio
import json
from datetime import datetime, timezone

import httpx
import pytest

from log_analytics.api.services.search_service import SearchBackendError, SearchService


def test_build_query_full() -> None:
    body = SearchService._build_query(
        query="timeout",
        service="checkout",
        level="ERROR",
        from_ts=datetime(2026, 7, 1, tzinfo=timezone.utc),
        to_ts=datetime(2026, 7, 2, tzinfo=timezone.utc),
        size=10,
        offset=20,
    )
    assert body["query"]["bool"]["must"] == [
        {"match": {"message": {"query": "timeout", "operator": "and"}}}
    ]
    filters = body["query"]["bool"]["filter"]
    assert {"term": {"service": "checkout"}} in filters
    assert {"term": {"level": "ERROR"}} in filters
    assert any("range" in f for f in filters)
    assert body["size"] == 10 and body["from"] == 20


def test_build_query_defaults_to_match_all() -> None:
    body = SearchService._build_query(
        query=None, service=None, level=None, from_ts=None, to_ts=None, size=5, offset=0
    )
    assert body["query"]["bool"]["must"] == [{"match_all": {}}]
    assert body["query"]["bool"]["filter"] == []


def _service_with(handler) -> SearchService:
    return SearchService("http://opensearch:9200", transport=httpx.MockTransport(handler))


def test_search_maps_hits_and_totals() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/la-logs/_search"
        body = json.loads(request.content)
        assert body["sort"] == [{"@timestamp": {"order": "desc"}}]
        return httpx.Response(
            200,
            json={
                "took": 3,
                "hits": {
                    "total": {"value": 2},
                    "hits": [
                        {"_source": {"service": "a", "message": "m1"}},
                        {"_source": {"service": "b", "message": "m2"}},
                    ],
                },
            },
        )

    result = asyncio.run(
        _service_with(handler).search_logs(
            query=None, service=None, level=None, from_ts=None, to_ts=None, size=50, offset=0
        )
    )
    assert result.total == 2
    assert result.took_ms == 3
    assert result.hits[0]["service"] == "a"


def test_non_200_raises_backend_error() -> None:
    service = _service_with(lambda req: httpx.Response(500, text="shard failure"))
    with pytest.raises(SearchBackendError, match="500"):
        asyncio.run(
            service.search_logs(
                query=None, service=None, level=None, from_ts=None, to_ts=None, size=1, offset=0
            )
        )


def test_transport_failure_raises_backend_error() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("refused", request=request)

    with pytest.raises(SearchBackendError):
        asyncio.run(
            _service_with(handler).search_logs(
                query=None, service=None, level=None, from_ts=None, to_ts=None, size=1, offset=0
            )
        )
