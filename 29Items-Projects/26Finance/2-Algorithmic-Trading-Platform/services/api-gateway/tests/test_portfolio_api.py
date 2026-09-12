"""API tests for positions / orders / PnL and auth enforcement."""

from __future__ import annotations

from decimal import Decimal
from uuid import uuid4

import pytest_asyncio
from api_gateway.main import create_app
from httpx import ASGITransport, AsyncClient

from trading_common.db import OrderRow, PositionRepository, session_scope


@pytest_asyncio.fixture
async def client(session_factory):
    app = create_app(session_factory=session_factory, auth_enabled=False)
    app.state.session_factory = session_factory
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        yield ac


async def test_pnl_summary_empty(client: AsyncClient) -> None:
    resp = await client.get("/api/v1/pnl")
    assert resp.status_code == 200
    body = resp.json()
    assert body["open_positions"] == 0
    assert body["total_pnl"] == "0"


async def test_positions_and_pnl(client: AsyncClient, session_factory) -> None:
    async with session_scope(session_factory) as session:
        repo = PositionRepository(session)
        await repo.upsert("AAPL", 100, Decimal("150.00"), Decimal("250.00"), Decimal("500.00"))

    positions = (await client.get("/api/v1/positions")).json()
    assert len(positions) == 1
    assert positions[0]["symbol"] == "AAPL"
    assert positions[0]["avg_price"] == "150.0000"  # Decimal-as-string

    pnl = (await client.get("/api/v1/pnl")).json()
    assert pnl["open_positions"] == 1
    assert pnl["realized_pnl"] == "250.0000"
    assert pnl["total_pnl"] == "750.0000"  # 250 + 500


async def test_orders_listing(client: AsyncClient, session_factory) -> None:
    # seed a strategy + order
    from trading_common.db import StrategyRow

    async with session_scope(session_factory) as session:
        strat = StrategyRow(
            name="s",
            klass="x",
            symbols=["AAPL"],
            params={},
            state="PAPER",
            max_position_qty=10,
            max_order_notional=Decimal("1"),
        )
        session.add(strat)
        await session.flush()
        session.add(
            OrderRow(
                client_order_id="c1",
                strategy_id=strat.id,
                symbol="AAPL",
                side="BUY",
                order_type="MARKET",
                quantity=100,
                status="FILLED",
                correlation_id=uuid4(),
            )
        )

    orders = (await client.get("/api/v1/orders")).json()
    assert len(orders) == 1 and orders[0]["client_order_id"] == "c1"


async def test_health(client: AsyncClient) -> None:
    assert (await client.get("/health")).json() == {"status": "ok"}


async def test_websocket_pnl_stream(client: AsyncClient, session_factory) -> None:
    # Use Starlette's TestClient for the websocket handshake.
    from starlette.testclient import TestClient

    app = create_app(session_factory=session_factory, auth_enabled=False)
    app.state.session_factory = session_factory
    with TestClient(app) as tc, tc.websocket_connect("/api/v1/ws/pnl") as ws:
        msg = ws.receive_json()
        assert "total_pnl" in msg and "open_positions" in msg


def _token(secret: str, role: str, *, expired: bool = False) -> str:
    import datetime as dt

    import jwt

    now = dt.datetime.now(dt.UTC)
    exp = now - dt.timedelta(minutes=5) if expired else now + dt.timedelta(minutes=30)
    return jwt.encode({"sub": "user-1", "role": role, "exp": exp}, secret, algorithm="HS256")


def _strategy_payload(name: str = "auth-strat") -> dict:
    return {
        "name": name,
        "class": "strategy_engine.strategies.momentum.EmaCrossoverStrategy",
        "symbols": ["AAPL"],
        "max_position_qty": 1,
        "max_order_notional": 1,
    }


async def test_auth_enforced_when_enabled(session_factory) -> None:
    secret = "unit-test-secret-at-least-32-bytes-long!"  # >= 32 bytes (HS256)
    app = create_app(session_factory=session_factory, auth_enabled=True)
    app.state.session_factory = session_factory
    app.state.jwt_secret = secret
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        # No token -> 401
        assert (await ac.post("/api/v1/strategies", json=_strategy_payload())).status_code == 401

        # Invalid signature -> 401
        bad = _token("a-completely-different-secret-32bytes!!", "trader")
        resp = await ac.post(
            "/api/v1/strategies",
            headers={"Authorization": f"Bearer {bad}"},
            json=_strategy_payload(),
        )
        assert resp.status_code == 401

        # Expired token -> 401
        expired = _token(secret, "trader", expired=True)
        resp = await ac.post(
            "/api/v1/strategies",
            headers={"Authorization": f"Bearer {expired}"},
            json=_strategy_payload(),
        )
        assert resp.status_code == 401

        # viewer role -> 403 (insufficient)
        viewer = _token(secret, "viewer")
        resp = await ac.post(
            "/api/v1/strategies",
            headers={"Authorization": f"Bearer {viewer}"},
            json=_strategy_payload(),
        )
        assert resp.status_code == 403

        # trader role -> allowed
        trader = _token(secret, "trader")
        resp = await ac.post(
            "/api/v1/strategies",
            headers={"Authorization": f"Bearer {trader}"},
            json=_strategy_payload(),
        )
        assert resp.status_code == 201

        # GET (viewer-level) is allowed for a viewer token
        resp = await ac.get("/api/v1/strategies", headers={"Authorization": f"Bearer {viewer}"})
        assert resp.status_code == 200


async def test_readiness_probe(client: AsyncClient) -> None:
    resp = await client.get("/health/ready")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ready"}


async def _seed_orders(session_factory, n: int) -> None:
    from trading_common.db import StrategyRow

    async with session_scope(session_factory) as session:
        strat = StrategyRow(
            name="s",
            klass="x",
            symbols=["AAPL"],
            params={},
            state="PAPER",
            max_position_qty=10,
            max_order_notional=Decimal("1"),
        )
        session.add(strat)
        await session.flush()
        for i in range(n):
            session.add(
                OrderRow(
                    client_order_id=f"c{i}",
                    strategy_id=strat.id,
                    symbol="AAPL",
                    side="BUY",
                    order_type="MARKET",
                    quantity=10,
                    status="FILLED",
                    correlation_id=uuid4(),
                )
            )


async def test_orders_pagination(client: AsyncClient, session_factory) -> None:
    await _seed_orders(session_factory, 3)
    assert len((await client.get("/api/v1/orders?limit=2")).json()) == 2
    assert len((await client.get("/api/v1/orders?limit=2&offset=2")).json()) == 1


async def test_orders_limit_is_bounded(client: AsyncClient) -> None:
    assert (await client.get("/api/v1/orders?limit=0")).status_code == 422
    assert (await client.get("/api/v1/orders?limit=5000")).status_code == 422
    assert (await client.get("/api/v1/orders?offset=-1")).status_code == 422


async def test_security_headers_present(client: AsyncClient) -> None:
    resp = await client.get("/health")
    assert resp.headers.get("X-Content-Type-Options") == "nosniff"
    assert resp.headers.get("X-Frame-Options") == "DENY"


async def test_unhandled_error_returns_clean_500(session_factory) -> None:
    def boom_factory():
        raise RuntimeError("db blew up")

    app = create_app(session_factory=session_factory, auth_enabled=False)
    app.state.session_factory = boom_factory  # any request using the DB now errors
    # raise_app_exceptions=False so we observe the 500 response the real client gets
    # (Starlette re-raises after sending it, for server-side logging).
    transport = ASGITransport(app=app, raise_app_exceptions=False)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        resp = await ac.get("/api/v1/strategies")
        assert resp.status_code == 500
        assert resp.json() == {"detail": "internal server error"}  # no internals leaked
