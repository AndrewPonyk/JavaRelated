from __future__ import annotations

import uuid


def _symbol() -> str:
    return f"A{uuid.uuid4().hex[:6].upper()}"


def test_create_get_list_asset(client, auth_headers):
    symbol = _symbol()
    created = client.post(
        "/api/v1/assets",
        json={"symbol": symbol, "name": "Acme", "asset_class": "equity", "currency": "USD"},
        headers=auth_headers,
    )
    assert created.status_code == 201
    asset_id = created.json()["id"]

    got = client.get(f"/api/v1/assets/{asset_id}", headers=auth_headers)
    assert got.status_code == 200
    assert got.json()["symbol"] == symbol

    listed = client.get("/api/v1/assets", headers=auth_headers)
    assert listed.status_code == 200
    assert any(a["id"] == asset_id for a in listed.json())


def test_duplicate_symbol_rejected(client, auth_headers):
    symbol = _symbol()
    payload = {"symbol": symbol, "name": "Dup"}
    assert client.post("/api/v1/assets", json=payload, headers=auth_headers).status_code == 201
    dup = client.post("/api/v1/assets", json=payload, headers=auth_headers)
    assert dup.status_code == 422


def test_get_missing_asset_404(client, auth_headers):
    resp = client.get("/api/v1/assets/999999", headers=auth_headers)
    assert resp.status_code == 404
    assert resp.json()["error"]["code"] == "not_found"


def test_seed_and_ingest_prices(client, auth_headers):
    symbol = _symbol()
    asset_id = client.post(
        "/api/v1/assets", json={"symbol": symbol, "name": "Seedco"}, headers=auth_headers
    ).json()["id"]

    seeded = client.post(f"/api/v1/assets/{asset_id}/seed-prices?days=300", headers=auth_headers)
    assert seeded.status_code == 201
    assert seeded.json()["bars_inserted"] == 300

    # Stub provider ingestion is offline + idempotent (existing dates skipped).
    ingested = client.post(f"/api/v1/assets/{asset_id}/ingest?days=300", headers=auth_headers)
    assert ingested.status_code == 201
    assert ingested.json()["provider"] == "stub"
    assert ingested.json()["bars_inserted"] == 0
