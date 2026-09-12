"""Integration tests for the category taxonomy CRUD (against in-memory SQLite)."""

from __future__ import annotations


def test_create_and_get_category(client):
    resp = client.post("/categories", json={"name": "electronics"})
    assert resp.status_code == 201
    created = resp.json()
    assert created["name"] == "electronics"
    cid = created["id"]

    got = client.get(f"/categories/{cid}")
    assert got.status_code == 200
    assert got.json()["name"] == "electronics"


def test_list_categories(client):
    client.post("/categories", json={"name": "books"})
    client.post("/categories", json={"name": "toys"})
    resp = client.get("/categories")
    assert resp.status_code == 200
    names = {c["name"] for c in resp.json()}
    assert {"books", "toys"} <= names


def test_create_with_parent(client):
    client.post("/categories", json={"name": "apparel"})
    resp = client.post("/categories", json={"name": "shoes", "parent": "apparel"})
    assert resp.status_code == 201


def test_create_with_missing_parent_404(client):
    resp = client.post("/categories", json={"name": "x", "parent": "nope"})
    assert resp.status_code == 404


def test_duplicate_name_conflict(client):
    client.post("/categories", json={"name": "unique-one"})
    resp = client.post("/categories", json={"name": "unique-one"})
    assert resp.status_code == 409
    assert resp.json()["error"]["code"] == "CONFLICT"


def test_update_category(client):
    cid = client.post("/categories", json={"name": "garden"}).json()["id"]
    resp = client.put(f"/categories/{cid}", json={"description": "outdoor goods"})
    assert resp.status_code == 200
    assert resp.json()["description"] == "outdoor goods"


def test_update_missing_404(client):
    resp = client.put("/categories/9999", json={"description": "x"})
    assert resp.status_code == 404


def test_delete_category(client):
    cid = client.post("/categories", json={"name": "temp"}).json()["id"]
    assert client.delete(f"/categories/{cid}").status_code == 204
    assert client.get(f"/categories/{cid}").status_code == 404


def test_pagination(client):
    for i in range(5):
        client.post("/categories", json={"name": f"cat-{i}"})
    resp = client.get("/categories", params={"limit": 2, "offset": 0})
    assert resp.status_code == 200
    assert len(resp.json()) == 2
