"""Dataset CRUD — happy paths, validation, and problem responses."""

from __future__ import annotations

import pytest


def test_create_then_fetch_dataset(client, created_dataset, valid_dataset):
    dataset_id = created_dataset["id"]

    fetched = client.get(f"/api/v1/datasets/{dataset_id}")
    assert fetched.status_code == 200
    body = fetched.json()
    assert body["name"] == "sales.orders"
    assert body["layer"] == "silver"
    assert body["s3_path"] == valid_dataset["s3_path"]

    listed = client.get("/api/v1/datasets", params={"layer": "silver"})
    assert listed.json()["total"] == 1
    assert client.get("/api/v1/datasets", params={"layer": "gold"}).json()["total"] == 0


def test_list_pagination(client, valid_dataset):
    for i in range(5):
        payload = {**valid_dataset, "name": f"sales.table_{i}"}
        assert client.post("/api/v1/datasets", json=payload).status_code == 201

    page = client.get("/api/v1/datasets", params={"limit": 2, "offset": 2}).json()
    assert page["total"] == 5
    assert [d["name"] for d in page["items"]] == ["sales.table_2", "sales.table_3"]


def test_list_exact_name_lookup(client, valid_dataset):
    client.post("/api/v1/datasets", json=valid_dataset)
    client.post("/api/v1/datasets", json={**valid_dataset, "name": "sales.orders_v2"})

    found = client.get("/api/v1/datasets", params={"name": "sales.orders"}).json()
    assert found["total"] == 1
    assert found["items"][0]["name"] == "sales.orders"
    assert client.get("/api/v1/datasets", params={"name": "nope.nope"}).json()["total"] == 0


def test_duplicate_name_is_conflict_problem(client, created_dataset, valid_dataset):
    response = client.post("/api/v1/datasets", json=valid_dataset)
    assert response.status_code == 409
    assert response.headers["content-type"].startswith("application/problem+json")
    assert "already exists" in response.json()["detail"]


def test_update_dataset(client, created_dataset):
    dataset_id = created_dataset["id"]
    response = client.patch(
        f"/api/v1/datasets/{dataset_id}",
        json={"description": "now curated", "owner_email": "new-owner@example.com"},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["description"] == "now curated"
    assert body["owner_email"] == "new-owner@example.com"


def test_update_rejects_bad_email(client, created_dataset):
    response = client.patch(
        f"/api/v1/datasets/{created_dataset['id']}", json={"owner_email": "nope"}
    )
    assert response.status_code == 422


def test_delete_dataset(client, created_dataset):
    dataset_id = created_dataset["id"]
    assert client.delete(f"/api/v1/datasets/{dataset_id}").status_code == 204
    assert client.get(f"/api/v1/datasets/{dataset_id}").status_code == 404
    assert client.delete(f"/api/v1/datasets/{dataset_id}").status_code == 404


@pytest.mark.parametrize(
    "field,value",
    [
        ("name", "Not Snake Case!"),
        ("name", "ab"),  # below minimum length
        ("name", "sales.9orders"),  # segment must start with a letter (Trino identifier rule)
        ("name", "sales..orders"),  # empty segment
        ("layer", "platinum"),
        ("owner_email", "not-an-email"),
        ("s3_path", "http://not-s3/bucket"),
    ],
)
def test_invalid_input_rejected(client, valid_dataset, field, value):
    assert client.post("/api/v1/datasets", json={**valid_dataset, field: value}).status_code == 422


def test_patch_with_explicit_null_owner_is_422_not_500(client, created_dataset):
    """Regression: explicit null for a required column must not surface as a DB error."""
    response = client.patch(f"/api/v1/datasets/{created_dataset['id']}", json={"owner_email": None})
    assert response.status_code == 422
    assert "cannot be null" in response.text

    # the dataset is untouched
    fetched = client.get(f"/api/v1/datasets/{created_dataset['id']}").json()
    assert fetched["owner_email"] == "data-eng@example.com"


def test_empty_patch_is_a_valid_noop(client, created_dataset):
    response = client.patch(f"/api/v1/datasets/{created_dataset['id']}", json={})
    assert response.status_code == 200
    assert response.json()["owner_email"] == "data-eng@example.com"


def test_responses_are_gzipped_when_large(client, valid_dataset):
    for i in range(30):
        client.post("/api/v1/datasets", json={**valid_dataset, "name": f"sales.gzip_{i:02d}"})
    response = client.get(
        "/api/v1/datasets", params={"limit": 200}, headers={"Accept-Encoding": "gzip"}
    )
    assert response.status_code == 200
    assert response.headers.get("content-encoding") == "gzip"


def test_missing_dataset_is_404_problem(client):
    response = client.get("/api/v1/datasets/00000000-0000-0000-0000-000000000000")
    assert response.status_code == 404
    assert response.json()["title"] == "Dataset not found"


def test_request_id_header_present(client):
    response = client.get("/api/v1/datasets")
    assert response.headers.get("x-request-id")

    echoed = client.get("/api/v1/datasets", headers={"X-Request-ID": "trace-123"})
    assert echoed.headers["x-request-id"] == "trace-123"
