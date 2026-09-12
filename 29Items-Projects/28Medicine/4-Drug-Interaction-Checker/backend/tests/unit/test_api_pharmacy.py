"""API tests for the authenticated /pharmacy endpoint (real JWT auth)."""

from app.api.deps import get_interaction_service
from tests.fakes import FakeInteractionService

_BODY = {"patient_ref": "patient-1", "medications": [{"name": "aspirin"}]}


def test_requires_authentication(client, app):
    app.dependency_overrides[get_interaction_service] = lambda: FakeInteractionService()
    assert client.post("/api/v1/pharmacy/check-batch", json=_BODY).status_code == 401


def test_invalid_token_rejected(client, app):
    app.dependency_overrides[get_interaction_service] = lambda: FakeInteractionService()
    resp = client.post(
        "/api/v1/pharmacy/check-batch",
        headers={"Authorization": "Bearer not.a.jwt"},
        json=_BODY,
    )
    assert resp.status_code == 401


def test_forbidden_without_scope(client, app, token):
    app.dependency_overrides[get_interaction_service] = lambda: FakeInteractionService()
    headers = {"Authorization": f"Bearer {token(scopes=['some:other'])}"}
    resp = client.post("/api/v1/pharmacy/check-batch", headers=headers, json=_BODY)
    assert resp.status_code == 403


def test_allowed_with_scope(client, app, token):
    app.dependency_overrides[get_interaction_service] = lambda: FakeInteractionService()
    headers = {"Authorization": f"Bearer {token(scopes=['pharmacy:check'])}"}
    resp = client.post("/api/v1/pharmacy/check-batch", headers=headers, json=_BODY)
    assert resp.status_code == 200
    assert resp.json()["highest_severity"] == "major"
