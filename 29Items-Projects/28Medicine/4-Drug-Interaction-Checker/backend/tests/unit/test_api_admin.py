"""API tests for the admin CRUD endpoints (require the 'admin' scope)."""

from app.api.deps import get_drug_service, get_interaction_service
from tests.fakes import FakeDrugService, FakeInteractionService


def test_admin_requires_auth(client):
    assert client.get("/api/v1/admin/drugs").status_code == 401


def test_admin_requires_scope(client, token):
    headers = {"Authorization": f"Bearer {token(scopes=['pharmacy:check'])}"}
    assert client.get("/api/v1/admin/drugs", headers=headers).status_code == 403


def test_create_drug(client, app, admin_headers):
    app.dependency_overrides[get_drug_service] = lambda: FakeDrugService()
    resp = client.post(
        "/api/v1/admin/drugs",
        headers=admin_headers,
        json={
            "rxcui": "1191",
            "name": "aspirin",
            "ingredients": [{"rxcui": "1191", "name": "aspirin", "class_ids": ["C1"]}],
            "class_ids": ["C1"],
        },
    )
    assert resp.status_code == 201
    assert resp.json()["rxcui"] == "1191"


def test_list_drugs(client, app, admin_headers):
    app.dependency_overrides[get_drug_service] = lambda: FakeDrugService()
    resp = client.get("/api/v1/admin/drugs", headers=admin_headers)
    assert resp.status_code == 200
    assert resp.json()["total"] == 1


def test_delete_drug(client, app, admin_headers):
    app.dependency_overrides[get_drug_service] = lambda: FakeDrugService(deleted=True)
    assert client.delete("/api/v1/admin/drugs/1191", headers=admin_headers).status_code == 204


def test_delete_drug_not_found(client, app, admin_headers):
    app.dependency_overrides[get_drug_service] = lambda: FakeDrugService(deleted=False)
    assert client.delete("/api/v1/admin/drugs/zzz", headers=admin_headers).status_code == 404


def test_upsert_class(client, app, admin_headers):
    app.dependency_overrides[get_drug_service] = lambda: FakeDrugService()
    resp = client.post(
        "/api/v1/admin/classes",
        headers=admin_headers,
        json={"class_id": "C1", "name": "NSAID", "class_type": "ATC"},
    )
    assert resp.status_code == 204


def test_create_interaction(client, app, admin_headers):
    app.dependency_overrides[get_interaction_service] = lambda: FakeInteractionService()
    resp = client.post(
        "/api/v1/admin/interactions",
        headers=admin_headers,
        json={"rxcui_a": "1191", "rxcui_b": "11289", "severity": "major"},
    )
    assert resp.status_code == 201
    assert resp.json()["severity"] == "major"


def test_list_interactions(client, app, admin_headers):
    app.dependency_overrides[get_interaction_service] = lambda: FakeInteractionService()
    resp = client.get("/api/v1/admin/interactions", headers=admin_headers)
    assert resp.status_code == 200
    assert resp.json()["total"] == 1


def test_delete_interaction(client, app, admin_headers):
    app.dependency_overrides[get_interaction_service] = lambda: FakeInteractionService(deleted=True)
    resp = client.delete("/api/v1/admin/interactions/1191/11289", headers=admin_headers)
    assert resp.status_code == 204


def test_delete_interaction_not_found(client, app, admin_headers):
    app.dependency_overrides[get_interaction_service] = lambda: FakeInteractionService(
        deleted=False
    )
    resp = client.delete("/api/v1/admin/interactions/1/2", headers=admin_headers)
    assert resp.status_code == 404
