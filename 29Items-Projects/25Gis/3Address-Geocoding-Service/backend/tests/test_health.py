"""Unit tests that do not require a database."""

from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.core.text import collapse_whitespace, normalize_key
from app.main import app
from app.services.nominatim import NominatimClient, _clamp_confidence


def test_health_check() -> None:
    client = TestClient(app)
    response = client.get("/api/v1/health")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert response.headers["x-content-type-options"] == "nosniff"
    assert "x-request-id" in response.headers


def test_lookup_rejects_short_input() -> None:
    client = TestClient(app)
    response = client.post("/api/v1/geocode/lookup", json={"query": "  a  "})

    assert response.status_code == 422
    body = response.json()
    assert body["error"]["code"] == "validation_error"
    assert "request_id" in body["error"]


def test_reverse_geocode_rejects_invalid_coordinates() -> None:
    client = TestClient(app)
    response = client.post("/api/v1/geocode/reverse", json={"latitude": 91, "longitude": 0})

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "validation_error"


def test_address_list_pagination_bounds() -> None:
    client = TestClient(app)
    response = client.get("/api/v1/addresses", params={"limit": 101, "offset": 0})

    assert response.status_code == 422


def test_normalize_key_lowercases_and_collapses() -> None:
    assert normalize_key("  1600  Pennsylvania   Ave  ") == "1600 pennsylvania ave"


def test_collapse_whitespace_preserves_case() -> None:
    assert collapse_whitespace("  1600  Pennsylvania   Ave  ") == "1600 Pennsylvania Ave"


def test_clamp_confidence_bounds() -> None:
    assert _clamp_confidence(1.5, 0.5) == 1.0
    assert _clamp_confidence(-2, 0.5) == 0.0
    assert _clamp_confidence(None, 0.42) == 0.42
    assert _clamp_confidence("not-a-number", 0.3) == 0.3


def test_parse_item_maps_provider_fields() -> None:
    parsed = NominatimClient._parse_item(
        {"display_name": "10 Downing St", "lat": "51.5034", "lon": "-0.1276", "place_id": 99},
        default_confidence=0.5,
    )
    assert parsed is not None
    assert parsed.formatted_address == "10 Downing St"
    assert parsed.latitude == 51.5034
    assert parsed.provider_place_id == "99"

    assert NominatimClient._parse_item({"lat": "1"}, default_confidence=0.5) is None


async def test_nominatim_search_is_cached(monkeypatch) -> None:
    client = NominatimClient(get_settings())
    calls = {"count": 0}

    async def fake_get(path: str, params: dict) -> object:
        calls["count"] += 1
        return [{"display_name": "X", "lat": "1.0", "lon": "2.0", "place_id": 7, "importance": 0.4}]

    monkeypatch.setattr(client, "_get", fake_get)

    first = await client.search("same query", 5)
    second = await client.search("same query", 5)

    assert calls["count"] == 1  # second call served from cache
    assert first == second
    assert first[0].provider_place_id == "7"
    assert first[0].confidence == 0.4
