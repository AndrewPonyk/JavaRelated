"""API tests for strategy CRUD against a real in-memory database."""

from __future__ import annotations

import pytest_asyncio
from api_gateway.main import create_app
from httpx import ASGITransport, AsyncClient


@pytest_asyncio.fixture
async def client(session_factory):
    app = create_app(session_factory=session_factory, auth_enabled=False)
    app.state.session_factory = session_factory  # ASGITransport doesn't run lifespan
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        yield ac


def _payload(name: str = "EMA Crossover") -> dict:
    return {
        "name": name,
        "class": "strategy_engine.strategies.momentum.EmaCrossoverStrategy",
        "symbols": ["aapl", "msft"],
        "params": {"fast": 12, "slow": 26},
        "max_position_qty": 1000,
        "max_order_notional": 250000,
    }


async def test_create_then_get_roundtrip(client: AsyncClient) -> None:
    resp = await client.post("/api/v1/strategies", json=_payload())
    assert resp.status_code == 201, resp.text
    body = resp.json()
    assert body["state"] == "DRAFT"
    assert body["symbols"] == ["AAPL", "MSFT"]
    assert body["class"].endswith("EmaCrossoverStrategy")

    got = await client.get(f"/api/v1/strategies/{body['id']}")
    assert got.status_code == 200
    assert got.json()["name"] == "EMA Crossover"


async def test_list_returns_created(client: AsyncClient) -> None:
    await client.post("/api/v1/strategies", json=_payload("one"))
    await client.post("/api/v1/strategies", json=_payload("two"))
    resp = await client.get("/api/v1/strategies")
    assert resp.status_code == 200
    assert {s["name"] for s in resp.json()} == {"one", "two"}


async def test_duplicate_name_conflicts(client: AsyncClient) -> None:
    await client.post("/api/v1/strategies", json=_payload("dup"))
    resp = await client.post("/api/v1/strategies", json=_payload("dup"))
    assert resp.status_code == 409


async def test_create_rejects_unknown_field(client: AsyncClient) -> None:
    resp = await client.post("/api/v1/strategies", json=_payload() | {"sneaky": True})
    assert resp.status_code == 422


async def test_create_rejects_empty_symbols(client: AsyncClient) -> None:
    resp = await client.post("/api/v1/strategies", json=_payload() | {"symbols": []})
    assert resp.status_code == 422


async def test_get_missing_returns_404(client: AsyncClient) -> None:
    resp = await client.get("/api/v1/strategies/00000000-0000-0000-0000-000000000000")
    assert resp.status_code == 404


async def test_lifecycle_transitions(client: AsyncClient) -> None:
    sid = (await client.post("/api/v1/strategies", json=_payload())).json()["id"]
    # DRAFT -> LIVE illegal
    assert (
        await client.patch(f"/api/v1/strategies/{sid}", json={"state": "LIVE"})
    ).status_code == 409
    # DRAFT -> BACKTESTING -> PAPER -> LIVE legal
    for state in ("BACKTESTING", "PAPER", "LIVE"):
        r = await client.patch(f"/api/v1/strategies/{sid}", json={"state": state})
        assert r.status_code == 200, r.text
        assert r.json()["state"] == state


async def test_update_params_and_limits(client: AsyncClient) -> None:
    sid = (await client.post("/api/v1/strategies", json=_payload())).json()["id"]
    r = await client.patch(
        f"/api/v1/strategies/{sid}",
        json={"params": {"fast": 5}, "max_position_qty": 500},
    )
    assert r.status_code == 200
    assert r.json()["params"] == {"fast": 5}
    assert r.json()["max_position_qty"] == 500


async def test_halt_always_allowed(client: AsyncClient) -> None:
    sid = (await client.post("/api/v1/strategies", json=_payload())).json()["id"]
    r = await client.post(f"/api/v1/strategies/{sid}/halt")
    assert r.status_code == 200
    assert r.json()["state"] == "HALTED"


async def test_cannot_delete_live(client: AsyncClient) -> None:
    sid = (await client.post("/api/v1/strategies", json=_payload())).json()["id"]
    for state in ("BACKTESTING", "PAPER", "LIVE"):
        await client.patch(f"/api/v1/strategies/{sid}", json={"state": state})
    assert (await client.delete(f"/api/v1/strategies/{sid}")).status_code == 409
    # HALT then delete works
    await client.post(f"/api/v1/strategies/{sid}/halt")
    assert (await client.delete(f"/api/v1/strategies/{sid}")).status_code == 204


async def test_delete_removes_strategy(client: AsyncClient) -> None:
    sid = (await client.post("/api/v1/strategies", json=_payload())).json()["id"]
    assert (await client.delete(f"/api/v1/strategies/{sid}")).status_code == 204
    assert (await client.get(f"/api/v1/strategies/{sid}")).status_code == 404
