"""API-contract tests over the ASGI app (infrastructure mocked via DI overrides).
True end-to-end behavior against real ES/PG/Redis lives in test_full_stack.py."""

from unittest.mock import AsyncMock

from httpx import AsyncClient


async def test_search_returns_hits_and_facets(client: AsyncClient) -> None:
    response = await client.get(
        "/api/v1/search", params={"q": "wireless headphones", "brand": "sony"}
    )

    assert response.status_code == 200
    data = response.json()
    assert data["meta"]["total"] == 2
    assert len(data["hits"]) == 2
    assert data["hits"][0]["sku"] == "SONY-WH1000"

    brand_facet = next(f for f in data["facets"] if f["name"] == "brand")
    assert brand_facet["values"][0] == {"value": "sony", "count": 2, "selected": True}


async def test_search_logs_an_impression_event(client: AsyncClient, events_mock: AsyncMock) -> None:
    response = await client.get(
        "/api/v1/search", params={"q": "tv"}, headers={"X-Session-ID": "sess-1"}
    )
    assert response.status_code == 200

    events_mock.log_search.assert_called_once()
    kwargs = events_mock.log_search.call_args.kwargs
    assert kwargs["query"] == "tv"
    assert kwargs["session_id"] == "sess-1"
    assert kwargs["results_count"] == 2
    assert len(kwargs["shown_product_ids"]) == 2


async def test_search_validates_page_bounds(client: AsyncClient) -> None:
    response = await client.get("/api/v1/search", params={"q": "tv", "page": 0})
    assert response.status_code == 422


async def test_search_rejects_inverted_price_range(client: AsyncClient) -> None:
    response = await client.get(
        "/api/v1/search", params={"q": "tv", "price_min": 500, "price_max": 100}
    )
    assert response.status_code == 422
    assert response.json()["code"] == "invalid_search_query"


async def test_search_maps_es_outage_to_503_problem(
    client: AsyncClient, es_mock: AsyncMock
) -> None:
    from elasticsearch import TransportError

    es_mock.search.side_effect = TransportError("boom")
    response = await client.get("/api/v1/search", params={"q": "tv"})

    assert response.status_code == 503
    body = response.json()
    assert body["code"] == "search_unavailable"
    assert response.headers["content-type"].startswith("application/problem+json")
    assert response.headers["retry-after"] == "5"


async def test_suggest_roundtrip(client: AsyncClient, es_mock: AsyncMock) -> None:
    es_mock.search.return_value = {
        "suggest": {"product_suggest": [{"options": [{"text": "laptop"}]}]}
    }
    response = await client.get("/api/v1/suggest", params={"q": "lapt"})

    assert response.status_code == 200
    assert response.json() == {"query": "lapt", "suggestions": ["laptop"]}


async def test_click_event_accepted(client: AsyncClient, events_mock: AsyncMock) -> None:
    response = await client.post(
        "/api/v1/events/click",
        json={
            "query": "tv",
            "product_id": "0b6cbb26-0aa7-4d1c-8d6d-8e5a2f7f3a11",
            "position": 2,
        },
        headers={"X-Session-ID": "sess-9"},
    )
    assert response.status_code == 204
    events_mock.log_click.assert_awaited_once()
    assert events_mock.log_click.call_args.kwargs["session_id"] == "sess-9"


async def test_click_event_validates_payload(client: AsyncClient) -> None:
    response = await client.post(
        "/api/v1/events/click",
        json={"query": "", "product_id": "not-a-uuid", "position": -1},
    )
    assert response.status_code == 422


async def test_admin_requires_api_key(client: AsyncClient) -> None:
    response = await client.post("/api/v1/admin/reindex")
    assert response.status_code == 401


async def test_admin_reindex_enqueues_catalog(client: AsyncClient, test_app) -> None:  # type: ignore[no-untyped-def]
    from types import SimpleNamespace

    from app.api import deps

    fake_db = AsyncMock()
    fake_db.execute.side_effect = [SimpleNamespace(rowcount=5), SimpleNamespace(rowcount=2)]

    async def override_db():  # type: ignore[no-untyped-def]
        yield fake_db

    test_app.dependency_overrides[deps.get_db] = override_db
    response = await client.post("/api/v1/admin/reindex", headers={"X-API-Key": "change-me"})

    assert response.status_code == 202
    assert response.json() == {"status": "accepted", "enqueued": 7}
    fake_db.commit.assert_awaited_once()


async def test_health_liveness(client: AsyncClient) -> None:
    response = await client.get("/healthz")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


async def test_readiness_degrades_without_failing_when_es_is_up(
    client: AsyncClient, test_app, es_mock: AsyncMock, redis_mock: AsyncMock, monkeypatch
) -> None:  # type: ignore[no-untyped-def]
    # readyz reads app.state directly (infrastructure contract, not DI).
    test_app.state.es = es_mock
    test_app.state.redis = redis_mock
    es_mock.ping.return_value = True

    def broken_session_factory():  # deterministic PG outage
        raise ConnectionError("pg down")

    from app.api.v1.endpoints import health

    monkeypatch.setattr(health, "async_session_factory", broken_session_factory)

    response = await client.get("/readyz")

    # ES up -> ready (200), PG outage reported as degraded, not fatal.
    assert response.status_code == 200
    checks = response.json()["checks"]
    assert checks["elasticsearch"] is True
    assert checks["redis"] is True
    assert checks["postgres"] is False


async def test_readiness_fails_when_search_backend_down(
    client: AsyncClient, test_app, es_mock: AsyncMock, redis_mock: AsyncMock
) -> None:  # type: ignore[no-untyped-def]
    test_app.state.es = es_mock
    test_app.state.redis = redis_mock
    es_mock.ping.side_effect = ConnectionError("es down")

    response = await client.get("/readyz")

    assert response.status_code == 503
    assert response.json()["ready"] is False
