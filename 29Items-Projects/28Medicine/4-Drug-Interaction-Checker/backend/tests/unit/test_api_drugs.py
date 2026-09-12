"""API tests for /drugs endpoints (services overridden with fakes)."""

from app.api.deps import get_drug_service
from tests.fakes import FakeDrugService


def test_search(client, app):
    app.dependency_overrides[get_drug_service] = lambda: FakeDrugService()
    resp = client.get("/api/v1/drugs/search", params={"q": "aspirin"})
    assert resp.status_code == 200
    assert resp.json()["matches"][0]["rxcui"] == "1191"


def test_search_query_too_short(client, app):
    app.dependency_overrides[get_drug_service] = lambda: FakeDrugService()
    assert client.get("/api/v1/drugs/search", params={"q": "a"}).status_code == 422


def test_get_drug(client, app):
    app.dependency_overrides[get_drug_service] = lambda: FakeDrugService()
    resp = client.get("/api/v1/drugs/1191")
    assert resp.status_code == 200
    assert resp.json()["rxcui"] == "1191"


def test_get_drug_not_found(client, app):
    app.dependency_overrides[get_drug_service] = lambda: FakeDrugService(raise_get=True)
    resp = client.get("/api/v1/drugs/zzz")
    assert resp.status_code == 404
    assert resp.json()["error"]["code"] == "drug_not_found"
