"""API tests for /interactions endpoints."""

from app.api.deps import get_interaction_service
from tests.fakes import FakeInteractionService


def test_check(client, app):
    app.dependency_overrides[get_interaction_service] = lambda: FakeInteractionService()
    resp = client.post(
        "/api/v1/interactions/check",
        json={"drugs": [{"name": "aspirin"}, {"name": "warfarin"}], "include_ml_prediction": True},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["highest_severity"] == "major"
    assert len(body["interactions"]) == 1


def test_check_empty_drugs_rejected(client, app):
    app.dependency_overrides[get_interaction_service] = lambda: FakeInteractionService()
    assert client.post("/api/v1/interactions/check", json={"drugs": []}).status_code == 422


def test_check_invalid_drug_input(client, app):
    app.dependency_overrides[get_interaction_service] = lambda: FakeInteractionService()
    # Empty DrugInput violates the "at least one field" validator.
    resp = client.post("/api/v1/interactions/check", json={"drugs": [{}]})
    assert resp.status_code == 422


def test_check_pair(client, app):
    app.dependency_overrides[get_interaction_service] = lambda: FakeInteractionService()
    resp = client.get("/api/v1/interactions/1191/11289")
    assert resp.status_code == 200
    assert resp.json()["highest_severity"] == "major"
