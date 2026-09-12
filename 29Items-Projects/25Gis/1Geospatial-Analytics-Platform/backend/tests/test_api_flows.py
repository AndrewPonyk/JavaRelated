from fastapi.testclient import TestClient


def create_dataset(client: TestClient) -> dict:
    response = client.post(
        "/api/v1/datasets",
        json={
            "name": "Operational parcels",
            "description": "Integration test dataset",
            "source_type": "vector",
            "metadata_json": {"srid": 4326},
        },
    )
    assert response.status_code == 201, response.text
    return response.json()


def create_feature(client: TestClient, dataset_id: str, land_use: str = "urban") -> dict:
    response = client.post(
        f"/api/v1/datasets/{dataset_id}/features",
        json={
            "properties": {"land_use": land_use, "name": f"{land_use} sample"},
            "geometry": {"type": "Point", "coordinates": [30.5234, 50.4501]},
        },
    )
    assert response.status_code == 201, response.text
    return response.json()


def test_health_check(no_db_client: TestClient) -> None:
    response = no_db_client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert "X-Request-ID" in response.headers


def test_dataset_feature_analysis_and_classification_flow(client: TestClient) -> None:
    dataset = create_dataset(client)
    feature = create_feature(client, dataset["id"])

    feature_list = client.get(f"/api/v1/datasets/{dataset['id']}/features")
    assert feature_list.status_code == 200
    assert feature_list.json()[0]["id"] == feature["id"]

    bbox = client.get(
        f"/api/v1/analysis/datasets/{dataset['id']}/bbox",
        params={"min_x": 30, "min_y": 50, "max_x": 31, "max_y": 51},
    )
    assert bbox.status_code == 200
    assert len(bbox.json()) == 1

    summary = client.get(f"/api/v1/analysis/datasets/{dataset['id']}/summary")
    assert summary.status_code == 200
    assert summary.json()["classes"]["urban"] == 1

    job = client.post(
        "/api/v1/classification-jobs",
        params={"run_immediately": False},
        json={"dataset_id": dataset["id"], "model_version": "rules-v1"},
    )
    assert job.status_code == 202, job.text

    run = client.post(f"/api/v1/classification-jobs/{job.json()['id']}/run")
    assert run.status_code == 200, run.text
    assert run.json()["status"] == "succeeded"
    assert run.json()["metrics"]["class_counts"]["urban"] == 1


def test_layer_crud_flow(client: TestClient) -> None:
    dataset = create_dataset(client)

    created = client.post(
        "/api/v1/layers",
        json={
            "dataset_id": dataset["id"],
            "name": "Parcels WMS",
            "layer_type": "wms",
            "style": "default",
            "is_public": True,
        },
    )
    assert created.status_code == 201, created.text

    layer_id = created.json()["id"]
    updated = client.patch(f"/api/v1/layers/{layer_id}", json={"style": "parcel-fill"})
    assert updated.status_code == 200
    assert updated.json()["style"] == "parcel-fill"

    listed = client.get("/api/v1/layers", params={"dataset_id": dataset["id"]})
    assert listed.status_code == 200
    assert len(listed.json()) == 1

    deleted = client.delete(f"/api/v1/layers/{layer_id}")
    assert deleted.status_code == 204


def test_geojson_import_and_proximity(client: TestClient) -> None:
    dataset = create_dataset(client)
    response = client.post(
        f"/api/v1/datasets/{dataset['id']}/features/import-geojson",
        json={
            "type": "FeatureCollection",
            "features": [
                {
                    "type": "Feature",
                    "properties": {"land_use": "water"},
                    "geometry": {"type": "Point", "coordinates": [30.55, 50.46]},
                }
            ],
        },
    )
    assert response.status_code == 201, response.text

    nearby = client.get(
        f"/api/v1/analysis/datasets/{dataset['id']}/proximity",
        params={"longitude": 30.5234, "latitude": 50.4501, "radius_meters": 10_000},
    )
    assert nearby.status_code == 200
    assert len(nearby.json()) == 1


def test_user_and_role_management(client: TestClient) -> None:
    role = client.post("/api/v1/users/roles", json={"name": "reviewer", "description": "Reviews GIS outputs"})
    assert role.status_code == 201, role.text

    user = client.post(
        "/api/v1/users",
        json={
            "email": "analyst@example.com",
            "display_name": "Example Analyst",
            "identity_subject": "oidc|analyst",
            "roles": ["reviewer"],
        },
    )
    assert user.status_code == 201, user.text
    assert user.json()["roles"] == ["reviewer"]

    updated = client.patch(f"/api/v1/users/{user.json()['id']}", json={"display_name": "Senior Analyst"})
    assert updated.status_code == 200
    assert updated.json()["display_name"] == "Senior Analyst"


def test_validation_errors_use_stable_error_shape(client: TestClient) -> None:
    response = client.post("/api/v1/datasets", json={"name": "", "source_type": "invalid", "metadata_json": {}})

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "validation_error"


def test_invalid_bbox_returns_stable_error_without_database(no_db_client: TestClient) -> None:
    response = no_db_client.get(
        "/api/v1/analysis/datasets/00000000-0000-0000-0000-000000000000/bbox",
        params={"min_x": 10, "min_y": 10, "max_x": 1, "max_y": 20},
    )

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "invalid_bbox"
    assert response.headers["X-Content-Type-Options"] == "nosniff"
