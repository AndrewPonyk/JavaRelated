"""Dataset schema versions — monotonic numbering and validation."""

from __future__ import annotations

SCHEMA_V1 = {"order_id": "string", "amount": "double"}
SCHEMA_V2 = {"order_id": "string", "amount": "decimal(18,2)", "currency": "string"}


def test_versions_auto_increment(client, created_dataset):
    dataset_id = created_dataset["id"]

    first = client.post(
        f"/api/v1/datasets/{dataset_id}/versions",
        json={"schema_json": SCHEMA_V1, "row_count": 100},
    )
    assert first.status_code == 201, first.text
    assert first.json()["version"] == 1

    second = client.post(f"/api/v1/datasets/{dataset_id}/versions", json={"schema_json": SCHEMA_V2})
    assert second.json()["version"] == 2
    assert second.json()["row_count"] is None


def test_versions_listed_newest_first(client, created_dataset):
    dataset_id = created_dataset["id"]
    for schema in (SCHEMA_V1, SCHEMA_V2):
        client.post(f"/api/v1/datasets/{dataset_id}/versions", json={"schema_json": schema})

    listed = client.get(f"/api/v1/datasets/{dataset_id}/versions")
    assert listed.status_code == 200
    versions = listed.json()
    assert [v["version"] for v in versions] == [2, 1]
    assert versions[0]["schema_json"] == SCHEMA_V2


def test_version_for_unknown_dataset_is_404(client):
    missing = "00000000-0000-0000-0000-000000000000"
    assert client.get(f"/api/v1/datasets/{missing}/versions").status_code == 404
    response = client.post(f"/api/v1/datasets/{missing}/versions", json={"schema_json": SCHEMA_V1})
    assert response.status_code == 404


def test_version_validation(client, created_dataset):
    dataset_id = created_dataset["id"]
    assert (
        client.post(f"/api/v1/datasets/{dataset_id}/versions", json={"schema_json": {}}).status_code
        == 422
    )
    assert (
        client.post(
            f"/api/v1/datasets/{dataset_id}/versions",
            json={"schema_json": SCHEMA_V1, "row_count": -1},
        ).status_code
        == 422
    )


def test_schema_json_size_limits(client, created_dataset):
    dataset_id = created_dataset["id"]
    too_many_columns = {f"col_{i}": "string" for i in range(501)}
    assert (
        client.post(
            f"/api/v1/datasets/{dataset_id}/versions", json={"schema_json": too_many_columns}
        ).status_code
        == 422
    )
    oversized_value = {"a": "x" * 100_001}
    assert (
        client.post(
            f"/api/v1/datasets/{dataset_id}/versions", json={"schema_json": oversized_value}
        ).status_code
        == 422
    )


def test_version_write_race_maps_to_409(client, created_dataset, monkeypatch):
    """Regression: a lost unique-constraint race must be a retryable 409, not a 500."""
    from sqlalchemy.exc import IntegrityError
    from sqlalchemy.orm import Session

    original_flush = Session.flush
    fired = {"done": False}

    def racing_flush(self, *args, **kwargs):
        original_flush(self, *args, **kwargs)
        if not fired["done"]:
            fired["done"] = True
            raise IntegrityError("uq_dataset_version", None, Exception("duplicate key"))

    monkeypatch.setattr(Session, "flush", racing_flush)
    response = client.post(
        f"/api/v1/datasets/{created_dataset['id']}/versions", json={"schema_json": SCHEMA_V1}
    )
    assert response.status_code == 409
    assert response.json()["title"] == "Concurrent modification"


def test_deleting_dataset_cascades_versions(client, created_dataset):
    dataset_id = created_dataset["id"]
    client.post(f"/api/v1/datasets/{dataset_id}/versions", json={"schema_json": SCHEMA_V1})
    assert client.delete(f"/api/v1/datasets/{dataset_id}").status_code == 204
    assert client.get(f"/api/v1/datasets/{dataset_id}/versions").status_code == 404
