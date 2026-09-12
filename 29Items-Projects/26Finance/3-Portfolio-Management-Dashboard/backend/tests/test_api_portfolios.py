from __future__ import annotations

import uuid


def _new_asset(client, headers) -> int:
    symbol = f"P{uuid.uuid4().hex[:6].upper()}"
    aid = client.post(
        "/api/v1/assets", json={"symbol": symbol, "name": f"{symbol} Inc"}, headers=headers
    ).json()["id"]
    client.post(f"/api/v1/assets/{aid}/seed-prices?days=300", headers=headers)
    return aid


def test_create_and_get_portfolio(client, auth_headers, portfolio):
    pid = portfolio["id"]
    assert len(portfolio["holdings"]) == 3

    got = client.get(f"/api/v1/portfolios/{pid}", headers=auth_headers)
    assert got.status_code == 200
    assert got.json()["name"] == "Test Portfolio"

    listed = client.get("/api/v1/portfolios", headers=auth_headers)
    assert any(p["id"] == pid for p in listed.json())


def test_create_portfolio_unknown_asset_404(client, auth_headers):
    resp = client.post(
        "/api/v1/portfolios",
        json={"name": "Bad", "holdings": [{"asset_id": 999999, "quantity": 1}]},
        headers=auth_headers,
    )
    assert resp.status_code == 404


def test_ownership_isolation(client, auth_headers, other_headers, portfolio):
    pid = portfolio["id"]
    # The second user must not see or fetch the first user's portfolio.
    assert client.get(f"/api/v1/portfolios/{pid}", headers=other_headers).status_code == 404
    assert client.get("/api/v1/portfolios", headers=other_headers).json() == []


def test_update_portfolio(client, auth_headers, portfolio):
    pid = portfolio["id"]
    resp = client.patch(
        f"/api/v1/portfolios/{pid}",
        json={"name": "Renamed", "description": "new desc"},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert resp.json()["name"] == "Renamed"
    assert resp.json()["description"] == "new desc"


def test_holdings_crud(client, auth_headers, portfolio):
    pid = portfolio["id"]
    asset_id = _new_asset(client, auth_headers)

    added = client.post(
        f"/api/v1/portfolios/{pid}/holdings",
        json={"asset_id": asset_id, "quantity": 5, "cost_basis": 50},
        headers=auth_headers,
    )
    assert added.status_code == 201
    holding_id = added.json()["id"]

    # Duplicate holding for same asset is rejected.
    dup = client.post(
        f"/api/v1/portfolios/{pid}/holdings",
        json={"asset_id": asset_id, "quantity": 1},
        headers=auth_headers,
    )
    assert dup.status_code == 422

    updated = client.patch(
        f"/api/v1/portfolios/{pid}/holdings/{holding_id}",
        json={"quantity": 12},
        headers=auth_headers,
    )
    assert updated.status_code == 200
    assert float(updated.json()["quantity"]) == 12.0

    deleted = client.delete(f"/api/v1/portfolios/{pid}/holdings/{holding_id}", headers=auth_headers)
    assert deleted.status_code == 204


def test_delete_portfolio(client, auth_headers, portfolio):
    pid = portfolio["id"]
    assert client.delete(f"/api/v1/portfolios/{pid}", headers=auth_headers).status_code == 204
    assert client.get(f"/api/v1/portfolios/{pid}", headers=auth_headers).status_code == 404
