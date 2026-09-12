"""Audit trail — every mutation leaves a queryable event."""

from __future__ import annotations


def _actions_for(client, entity_id: str) -> list[str]:
    body = client.get(
        "/api/v1/audit", params={"entity_type": "dataset", "entity_id": entity_id}
    ).json()
    return [event["action"] for event in body["items"]]


def test_full_lifecycle_is_audited(client, created_dataset):
    dataset_id = created_dataset["id"]

    client.patch(f"/api/v1/datasets/{dataset_id}", json={"description": "updated"})
    client.post(f"/api/v1/datasets/{dataset_id}/versions", json={"schema_json": {"a": "string"}})
    client.delete(f"/api/v1/datasets/{dataset_id}")

    actions = _actions_for(client, dataset_id)
    assert set(actions) == {
        "dataset.created",
        "dataset.updated",
        "dataset.version_added",
        "dataset.deleted",
    }
    # audit rows survive the dataset's deletion — that is the point of an audit log
    assert len(actions) == 4


def test_audit_events_carry_actor_and_details(client, created_dataset, valid_dataset):
    events = client.get("/api/v1/audit", params={"entity_id": created_dataset["id"]}).json()
    created = next(e for e in events["items"] if e["action"] == "dataset.created")
    assert created["actor"] == "dev@localhost"  # dev-mode principal
    assert created["details"] == {"name": valid_dataset["name"]}
    assert created["entity_type"] == "dataset"


def test_audit_filters_and_pagination(client, valid_dataset):
    ids = []
    for i in range(3):
        response = client.post(
            "/api/v1/datasets", json={**valid_dataset, "name": f"sales.audit_{i}"}
        )
        ids.append(response.json()["id"])

    everything = client.get("/api/v1/audit").json()
    assert everything["total"] == 3

    only_one = client.get("/api/v1/audit", params={"entity_id": ids[0]}).json()
    assert only_one["total"] == 1

    by_actor = client.get("/api/v1/audit", params={"actor": "nobody@example.com"}).json()
    assert by_actor["total"] == 0

    page = client.get("/api/v1/audit", params={"limit": 2, "offset": 2}).json()
    assert page["total"] == 3
    assert len(page["items"]) == 1
