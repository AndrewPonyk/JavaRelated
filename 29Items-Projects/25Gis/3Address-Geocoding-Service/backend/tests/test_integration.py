"""Integration tests against a real PostGIS database.

These exercise the persistence, trigram search, and spatial reverse-geocoding code
that replaced the original stubs. They auto-skip when no database is reachable
(see ``conftest.DB_AVAILABLE``).
"""

from app.services.nominatim import ProviderResult

MANHATTAN = {"latitude": 40.7484, "longitude": -73.9857}


async def test_address_crud_flow(client) -> None:
    payload = {
        "formatted_address": "350 5th Ave, New York, NY",
        "latitude": MANHATTAN["latitude"],
        "longitude": MANHATTAN["longitude"],
        "confidence": 0.95,
        "source": "manual",
    }

    created = await client.post("/api/v1/addresses", json=payload)
    assert created.status_code == 201
    address_id = created.json()["id"]
    assert isinstance(address_id, int)

    listed = await client.get("/api/v1/addresses", params={"limit": 10, "offset": 0})
    assert listed.status_code == 200
    assert listed.json()["total"] == 1

    fetched = await client.get(f"/api/v1/addresses/{address_id}")
    assert fetched.status_code == 200
    assert fetched.json()["formatted_address"] == payload["formatted_address"]

    updated = await client.put(
        f"/api/v1/addresses/{address_id}",
        json=payload | {"formatted_address": "Empire State Building"},
    )
    assert updated.status_code == 200
    assert updated.json()["formatted_address"] == "Empire State Building"

    deleted = await client.delete(f"/api/v1/addresses/{address_id}")
    assert deleted.status_code == 204

    missing = await client.get(f"/api/v1/addresses/{address_id}")
    assert missing.status_code == 404
    assert missing.json()["error"]["message"] == "Address not found"


async def test_persistence_survives_and_geom_is_generated(client) -> None:
    # Create via API, then reverse-geocode the exact point: only works if the row
    # persisted AND the generated geography column was populated for ST_DWithin.
    await client.post(
        "/api/v1/addresses",
        json={**MANHATTAN, "formatted_address": "350 5th Ave", "source": "manual"},
    )

    reverse = await client.post("/api/v1/geocode/reverse", json=MANHATTAN)
    assert reverse.status_code == 200
    body = reverse.json()
    assert body["source"] == "manual"
    assert body["formatted_address"] == "350 5th Ave"


async def test_forward_lookup_local_trigram_fallback(client, fake_nominatim) -> None:
    fake_nominatim.search_results = []  # force local search path
    await client.post(
        "/api/v1/addresses",
        json={**MANHATTAN, "formatted_address": "350 5th Avenue, New York", "source": "manual"},
    )

    response = await client.post("/api/v1/geocode/lookup", json={"query": "350 5th ave  new york"})
    assert response.status_code == 200
    body = response.json()
    assert body["normalized_query"] == "350 5th ave new york"
    assert body["best_match"]["source"] == "manual"
    assert body["best_match"]["formatted_address"] == "350 5th Avenue, New York"


async def test_forward_lookup_misses_when_nothing_matches(client, fake_nominatim) -> None:
    fake_nominatim.search_results = []
    response = await client.post("/api/v1/geocode/lookup", json={"query": "nowhere at all xyz"})
    assert response.status_code == 404
    assert response.json()["error"]["message"] == "No address match found"


async def test_forward_lookup_provider_hit_persists_and_dedupes(client, fake_nominatim) -> None:
    fake_nominatim.search_results = [
        ProviderResult(
            formatted_address="1600 Pennsylvania Ave NW, Washington, DC",
            latitude=38.8977,
            longitude=-77.0365,
            confidence=0.9,
            provider_place_id="osm:way/1",
        )
    ]

    first = await client.post(
        "/api/v1/geocode/lookup", json={"query": "1600 Pennsylvania Ave NW"}
    )
    assert first.status_code == 200
    body = first.json()
    assert body["best_match"]["source"] == "nominatim"
    assert isinstance(body["best_match"]["id"], int)

    # Same provider place id must update the existing row, not create a duplicate.
    await client.post("/api/v1/geocode/lookup", json={"query": "1600 Pennsylvania Ave NW"})
    listed = await client.get("/api/v1/addresses")
    assert listed.json()["total"] == 1


async def test_reverse_provider_fallback_persists(client, fake_nominatim) -> None:
    fake_nominatim.reverse_result = ProviderResult(
        formatted_address="Provider Resolved Place",
        latitude=MANHATTAN["latitude"],
        longitude=MANHATTAN["longitude"],
        confidence=0.7,
        provider_place_id="osm:node/5",
    )

    response = await client.post("/api/v1/geocode/reverse", json=MANHATTAN)
    assert response.status_code == 200
    assert response.json()["source"] == "nominatim"

    listed = await client.get("/api/v1/addresses")
    assert listed.json()["total"] == 1


async def test_reverse_miss_returns_404(client, fake_nominatim) -> None:
    fake_nominatim.reverse_result = None
    response = await client.post(
        "/api/v1/geocode/reverse", json={"latitude": 10.0, "longitude": 10.0}
    )
    assert response.status_code == 404
    assert response.json()["error"]["message"] == "No nearby address found"


async def test_search_autocomplete_endpoint(client) -> None:
    for name in ["Central Park, New York", "Central Station, Amsterdam", "Hyde Park, London"]:
        await client.post(
            "/api/v1/addresses",
            json={**MANHATTAN, "formatted_address": name, "source": "manual"},
        )

    response = await client.get("/api/v1/geocode/search", params={"q": "central par"})
    assert response.status_code == 200
    results = response.json()
    assert results, "expected at least one trigram match"
    assert results[0]["formatted_address"] == "Central Park, New York"
    assert all(r["source"] == "manual" for r in results)


async def test_readiness_probe_ok(client) -> None:
    response = await client.get("/api/v1/health/ready")
    assert response.status_code == 200
    assert response.json() == {"status": "ready", "database": "ok"}
