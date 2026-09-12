"""Device registry CRUD against an in-memory repository."""

import pytest

from app.core.security import hash_api_key, split_device_key


@pytest.fixture()
def fake_registry(monkeypatch):
    store: dict[str, dict] = {}

    async def fake_insert(device, api_key_hash):
        store[device.device_id] = {"device": device, "hash": api_key_hash}

    async def fake_get(device_id):
        entry = store.get(device_id)
        return entry["device"] if entry else None

    async def fake_get_hash(device_id):
        entry = store.get(device_id)
        return entry["hash"] if entry else None

    async def fake_list():
        return [entry["device"] for entry in store.values()]

    async def fake_set_enabled(device_id, enabled):
        device = store[device_id]["device"]
        store[device_id]["device"] = device.model_copy(update={"enabled": enabled})

    async def fake_delete(device_id):
        store.pop(device_id, None)

    repo = "app.repositories.devices"
    monkeypatch.setattr(f"{repo}.insert", fake_insert)
    monkeypatch.setattr(f"{repo}.get", fake_get)
    monkeypatch.setattr(f"{repo}.get_api_key_hash", fake_get_hash)
    monkeypatch.setattr(f"{repo}.list_all", fake_list)
    monkeypatch.setattr(f"{repo}.set_enabled", fake_set_enabled)
    monkeypatch.setattr(f"{repo}.delete", fake_delete)
    return store


def _register(client, operator_headers, name="Boiler sensor"):
    resp = client.post(
        "/api/v1/devices",
        json={"name": name, "site": "lviv-lab", "device_type": "thermo"},
        headers=operator_headers,
    )
    assert resp.status_code == 201
    return resp.json()


def test_register_returns_key_once_and_stores_only_hash(client, operator_headers, fake_registry):
    created = _register(client, operator_headers)
    assert created["device_id"].startswith("dev-")
    device_id, secret = split_device_key(created["api_key"])
    assert device_id == created["device_id"]
    # only the hash is at rest
    assert fake_registry[device_id]["hash"] == hash_api_key(secret)
    assert secret not in str(fake_registry[device_id])


def test_register_requires_operator_role(client, viewer_headers, fake_registry):
    resp = client.post("/api/v1/devices", json={"name": "X"}, headers=viewer_headers)
    assert resp.status_code == 403
    assert resp.json()["error"]["code"] == "forbidden"


def test_register_requires_auth(client, fake_registry):
    assert client.post("/api/v1/devices", json={"name": "X"}).status_code == 401


def test_register_validates_name(client, operator_headers, fake_registry):
    resp = client.post("/api/v1/devices", json={"name": ""}, headers=operator_headers)
    assert resp.status_code == 422


def test_list_and_get(client, operator_headers, viewer_headers, fake_registry):
    created = _register(client, operator_headers)

    listing = client.get("/api/v1/devices", headers=viewer_headers)
    assert listing.status_code == 200
    assert [d["device_id"] for d in listing.json()] == [created["device_id"]]

    single = client.get(f"/api/v1/devices/{created['device_id']}", headers=viewer_headers)
    assert single.status_code == 200
    assert single.json()["name"] == "Boiler sensor"


def test_get_unknown_device_is_404(client, viewer_headers, fake_registry):
    resp = client.get("/api/v1/devices/dev-nope", headers=viewer_headers)
    assert resp.status_code == 404
    assert resp.json()["error"]["code"] == "not_found"


def test_disable_and_delete_lifecycle(client, operator_headers, fake_registry):
    created = _register(client, operator_headers)
    device_id = created["device_id"]

    resp = client.patch(
        f"/api/v1/devices/{device_id}/enabled",
        params={"enabled": False},
        headers=operator_headers,
    )
    assert resp.status_code == 200
    assert fake_registry[device_id]["device"].enabled is False

    resp = client.delete(f"/api/v1/devices/{device_id}", headers=operator_headers)
    assert resp.status_code == 204
    assert device_id not in fake_registry


def test_patch_unknown_device_is_404(client, operator_headers, fake_registry):
    resp = client.patch(
        "/api/v1/devices/dev-nope/enabled",
        params={"enabled": False},
        headers=operator_headers,
    )
    assert resp.status_code == 404


def test_mutations_require_operator(client, viewer_headers, operator_headers, fake_registry):
    created = _register(client, operator_headers)
    device_id = created["device_id"]

    patch = client.patch(
        f"/api/v1/devices/{device_id}/enabled",
        params={"enabled": False},
        headers=viewer_headers,
    )
    delete = client.delete(f"/api/v1/devices/{device_id}", headers=viewer_headers)
    assert patch.status_code == 403 and delete.status_code == 403
