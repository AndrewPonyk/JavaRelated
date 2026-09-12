from datetime import UTC, datetime

from fastapi.testclient import TestClient


def test_health_check(client: TestClient) -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["database"] == "ok"
    assert response.headers["X-Content-Type-Options"] == "nosniff"
    assert "X-Request-ID" in response.headers


def test_vehicle_driver_crud(client: TestClient) -> None:
    driver_response = client.post(
        "/api/v1/drivers",
        json={"name": "Ada Lovelace", "phone": "+380000000000", "email": "ada@example.com"},
    )
    assert driver_response.status_code == 201
    driver_id = driver_response.json()["id"]

    vehicle_response = client.post(
        "/api/v1/vehicles",
        json={"name": "Van 12", "license_plate": "AA-1200-AA", "status": "idle", "driver_id": driver_id},
    )
    assert vehicle_response.status_code == 201
    vehicle = vehicle_response.json()
    assert vehicle["driver_id"] == driver_id

    patch_response = client.patch(f"/api/v1/vehicles/{vehicle['id']}", json={"status": "maintenance"})
    assert patch_response.status_code == 200
    assert patch_response.json()["status"] == "maintenance"

    list_response = client.get("/api/v1/vehicles")
    assert list_response.status_code == 200
    assert len(list_response.json()) == 1

    paged_response = client.get("/api/v1/vehicles?limit=1&offset=0")
    assert paged_response.status_code == 200
    assert len(paged_response.json()) == 1

    delete_response = client.delete(f"/api/v1/vehicles/{vehicle['id']}")
    assert delete_response.status_code == 204


def test_telemetry_geofence_route_and_trip_flow(client: TestClient) -> None:
    vehicle = client.post(
        "/api/v1/vehicles",
        json={"name": "Courier 1", "license_plate": "KA-0001-AA", "status": "idle"},
    ).json()

    polygon = {
        "type": "Polygon",
        "coordinates": [
            [
                [30.48, 50.43],
                [30.58, 50.43],
                [30.58, 50.49],
                [30.48, 50.49],
                [30.48, 50.43],
            ]
        ],
    }
    geofence_response = client.post(
        "/api/v1/geofences",
        json={"name": "Central", "description": "Central zone", "boundary_geojson": polygon},
    )
    assert geofence_response.status_code == 201

    recorded_at = datetime(2026, 4, 26, 9, 0, tzinfo=UTC).isoformat()
    telemetry_response = client.post(
        "/api/v1/telemetry",
        json={
            "vehicle_id": vehicle["id"],
            "recorded_at": recorded_at,
            "latitude": 50.45,
            "longitude": 30.52,
            "speed_kph": 40,
            "heading_degrees": 120,
            "raw_payload": {"source": "test"},
        },
    )
    assert telemetry_response.status_code == 201

    events = client.get("/api/v1/geofences/events").json()
    assert len(events) == 1
    assert events[0]["event_type"] == "inside"

    trip_response = client.post(
        "/api/v1/trips",
        json={
            "vehicle_id": vehicle["id"],
            "name": "Depot to hub",
            "origin_latitude": 50.45,
            "origin_longitude": 30.52,
            "destination_latitude": 50.4017,
            "destination_longitude": 30.2525,
            "status": "planned",
        },
    )
    assert trip_response.status_code == 201
    playback = client.get(f"/api/v1/trips/{trip_response.json()['id']}/playback").json()
    assert len(playback) == 1

    prediction_response = client.post(
        "/api/v1/routes/predict",
        json={
            "vehicle_id": vehicle["id"],
            "destination_latitude": 50.4017,
            "destination_longitude": 30.2525,
        },
    )
    assert prediction_response.status_code == 201
    prediction = prediction_response.json()
    assert prediction["distance_km"] > 0
    assert prediction["eta_seconds"] >= 60


def test_validation_and_not_found_errors(client: TestClient) -> None:
    missing = client.get("/api/v1/vehicles/not-a-real-id")
    assert missing.status_code == 404
    assert missing.json()["error"]["code"] == "vehicle_not_found"

    invalid_geofence = client.post(
        "/api/v1/geofences",
        json={"name": "Invalid", "boundary_geojson": {"type": "Point", "coordinates": [30, 50]}},
    )
    assert invalid_geofence.status_code == 422

    invalid_page = client.get("/api/v1/vehicles?limit=0")
    assert invalid_page.status_code == 422
