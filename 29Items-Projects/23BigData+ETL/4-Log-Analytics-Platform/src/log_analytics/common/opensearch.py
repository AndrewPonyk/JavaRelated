"""Minimal shared OpenSearch REST plumbing (httpx).

One place defines how the platform talks to the cluster: base URL normalization,
basic auth, error surfacing, and a `transport` hook so every consumer is testable
with httpx.MockTransport. Deliberately not a full client — components build their
own request bodies and stay explicit about what they ask the cluster to do.
"""

from __future__ import annotations

from typing import Any

import httpx


class OpenSearchError(Exception):
    """Non-2xx response (or transport failure) from the cluster."""

    def __init__(self, message: str, status_code: int | None = None) -> None:
        super().__init__(message)
        self.status_code = status_code


def async_client(
    base_url: str,
    auth: tuple[str, str] | None = None,
    timeout: float = 10.0,
    transport: httpx.AsyncBaseTransport | None = None,
) -> httpx.AsyncClient:
    return httpx.AsyncClient(
        base_url=base_url.rstrip("/"),
        auth=auth,
        timeout=timeout,
        transport=transport,
    )


async def request_json(
    client: httpx.AsyncClient,
    method: str,
    path: str,
    json: Any | None = None,
    ok_statuses: tuple[int, ...] = (200, 201),
) -> dict[str, Any]:
    """Issue a request and return the parsed body, raising OpenSearchError otherwise."""
    try:
        resp = await client.request(method, path, json=json)
    except httpx.HTTPError as exc:
        raise OpenSearchError(f"{method} {path}: {exc}") from exc
    if resp.status_code not in ok_statuses:
        raise OpenSearchError(
            f"{method} {path} -> {resp.status_code}: {resp.text[:300]}",
            status_code=resp.status_code,
        )
    return resp.json() if resp.content else {}
