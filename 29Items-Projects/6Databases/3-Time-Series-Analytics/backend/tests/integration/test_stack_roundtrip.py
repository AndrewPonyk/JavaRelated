"""End-to-end flows against REAL Cassandra + Redis (marker: requires_stack).

Covers: migration idempotency, admin bootstrap + login, device registration,
per-device-key ingest, raw query round trip, live aggregates, catalog,
key scoping, disable semantics, and cleanup.
"""

from datetime import UTC, datetime, timedelta

import pytest
from fastapi.testclient import TestClient

from app.core.config import settings
from tests.integration.stack_utils import (
    check_redis_or_skip,
    connect_cassandra_or_skip,
    cql_statements,
)

pytestmark = pytest.mark.requires_stack


@pytest.fixture(scope="module")
def stack_session():
    cluster, session = connect_cassandra_or_skip()
    check_redis_or_skip()
    yield session
    cluster.shutdown()


def test_migrations_are_idempotent(stack_session):
    for _ in range(2):  # applying the whole set twice must be a no-op
        for statement in cql_statements():
            stack_session.execute(statement)


@pytest.fixture(scope="module")
def stack_client(stack_session):
    for statement in cql_statements():  # don't depend on test order
        stack_session.execute(statement)
    from app.main import create_app

    with TestClient(create_app()) as client:
        yield client


@pytest.fixture(scope="module")
def admin_headers(stack_client):
    resp = stack_client.post(
        "/api/v1/auth/token",
        json={"username": settings.admin_username, "password": settings.admin_password},
    )
    assert resp.status_code == 200, f"bootstrap admin login failed: {resp.text}"
    return {"Authorization": f"Bearer {resp.json()['access_token']}"}


def test_readiness_is_green(stack_client):
    resp = stack_client.get("/api/v1/health/ready")
    assert resp.status_code == 200
    assert resp.json()["checks"]["cassandra"] == "ok"
    assert resp.json()["checks"]["redis"] == "ok"


def test_full_device_lifecycle_roundtrip(stack_client, admin_headers):
    # -- register ------------------------------------------------------------
    resp = stack_client.post(
        "/api/v1/devices",
        json={"name": "itest sensor", "site": "itest", "device_type": "sim"},
        headers=admin_headers,
    )
    assert resp.status_code == 201
    device = resp.json()
    device_id, api_key = device["device_id"], device["api_key"]

    try:
        # -- ingest with the per-device key -----------------------------------
        now = datetime.now(UTC)
        points = [
            {
                "device_id": device_id,
                "metric": "temperature",
                "ts": (now - timedelta(seconds=2 - i)).isoformat(),
                "value": 20.5 + i,
            }
            for i in range(3)
        ]
        resp = stack_client.post(
            "/api/v1/ingest", json={"points": points}, headers={"X-API-Key": api_key}
        )
        assert resp.status_code == 202
        assert resp.json() == {"accepted": 3, "rejected": 0}

        # -- raw query returns exactly what was written -----------------------
        resp = stack_client.get(
            f"/api/v1/devices/{device_id}/metrics/temperature", headers=admin_headers
        )
        assert resp.status_code == 200
        body = resp.json()
        assert body["source"] == "raw"
        assert [p["value"] for p in body["points"]] == [20.5, 21.5, 22.5]

        # -- series catalog was maintained by ingest --------------------------
        resp = stack_client.get(f"/api/v1/devices/{device_id}/metrics", headers=admin_headers)
        assert resp.json() == ["temperature"]

        # -- live aggregate from Redis ----------------------------------------
        resp = stack_client.get(
            f"/api/v1/devices/{device_id}/metrics/temperature/live",
            headers=admin_headers,
        )
        assert resp.status_code == 200
        assert resp.json()["latest"] == 22.5

        # -- anomaly feed exists (empty — worker hasn't scanned this device) ---
        resp = stack_client.get(f"/api/v1/devices/{device_id}/anomalies", headers=admin_headers)
        assert resp.status_code == 200

        # -- key scoping: this key cannot write another device's data ----------
        resp = stack_client.post(
            "/api/v1/ingest",
            json={"points": [{**points[0], "device_id": "dev-someone-else"}]},
            headers={"X-API-Key": api_key},
        )
        assert resp.status_code == 403

        # -- disabling the device stops its ingest (cache invalidated) ---------
        resp = stack_client.patch(
            f"/api/v1/devices/{device_id}/enabled",
            params={"enabled": False},
            headers=admin_headers,
        )
        assert resp.status_code == 200
        resp = stack_client.post(
            "/api/v1/ingest",
            json={"points": [{**points[0], "ts": datetime.now(UTC).isoformat()}]},
            headers={"X-API-Key": api_key},
        )
        assert resp.status_code == 202
        assert resp.json() == {"accepted": 0, "rejected": 1}
    finally:
        resp = stack_client.delete(f"/api/v1/devices/{device_id}", headers=admin_headers)
        assert resp.status_code == 204


def test_gateway_key_accepts_multi_device_batches(stack_client, admin_headers):
    a = stack_client.post(
        "/api/v1/devices", json={"name": "gw itest A"}, headers=admin_headers
    ).json()
    b = stack_client.post(
        "/api/v1/devices", json={"name": "gw itest B"}, headers=admin_headers
    ).json()
    try:
        now = datetime.now(UTC).isoformat()
        resp = stack_client.post(
            "/api/v1/ingest",
            json={
                "points": [
                    {"device_id": a["device_id"], "metric": "humidity", "ts": now, "value": 40.0},
                    {"device_id": b["device_id"], "metric": "humidity", "ts": now, "value": 45.0},
                ]
            },
            headers={"X-API-Key": settings.device_api_key},
        )
        assert resp.status_code == 202
        assert resp.json() == {"accepted": 2, "rejected": 0}
    finally:
        for device in (a, b):
            stack_client.delete(f"/api/v1/devices/{device['device_id']}", headers=admin_headers)
