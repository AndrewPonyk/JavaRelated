"""Readiness plumbing: cluster-health interpretation without a network."""

from __future__ import annotations

import asyncio

import httpx

from log_analytics.api.routers.health import opensearch_status


def _status_with(handler) -> str:
    return asyncio.run(
        opensearch_status("http://opensearch:9200", transport=httpx.MockTransport(handler))
    )


def test_green_cluster() -> None:
    assert _status_with(lambda req: httpx.Response(200, json={"status": "green"})) == "green"


def test_http_error_status() -> None:
    assert _status_with(lambda req: httpx.Response(503, json={})) == "error"


def test_unreachable() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("refused", request=request)

    assert _status_with(handler) == "unreachable"
