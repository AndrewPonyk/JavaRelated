"""Tests for the request-context (correlation ID) middleware."""

from app.api.deps import get_drug_service
from tests.fakes import FakeDrugService


def test_request_id_header_added(client, app):
    app.dependency_overrides[get_drug_service] = lambda: FakeDrugService()
    resp = client.get("/api/v1/drugs/search", params={"q": "aspirin"})
    assert resp.status_code == 200
    assert "X-Request-ID" in resp.headers


def test_request_id_is_propagated(client):
    resp = client.get("/api/v1/health/live", headers={"X-Request-ID": "corr-123"})
    assert resp.headers["X-Request-ID"] == "corr-123"
