"""End-to-end interaction check against real Neo4j (RxNorm faked, hermetic).

Run: pytest -m integration  (CI seeds the graph beforehand)
"""

import pytest
from fastapi.testclient import TestClient

from app.api.deps import get_rxnorm_service
from app.main import create_app
from app.models.drug import Drug, Ingredient

pytestmark = pytest.mark.integration


class _FakeRxNorm:
    """Maps names to the seeded ingredient RxCUIs (no external calls)."""

    _MAP = {
        "warfarin": Drug(
            rxcui="11289",
            name="warfarin",
            ingredients=[Ingredient(rxcui="11289", name="warfarin")],
        ),
        "aspirin": Drug(
            rxcui="1191",
            name="aspirin",
            ingredients=[Ingredient(rxcui="1191", name="aspirin")],
        ),
    }

    async def resolve(self, *, rxcui=None, ndc=None, name=None):
        return self._MAP.get((name or "").lower())


@pytest.fixture
def int_client():
    app = create_app()
    app.dependency_overrides[get_rxnorm_service] = lambda: _FakeRxNorm()
    with TestClient(app) as client:
        health = client.get("/api/v1/health").json()
        if not health.get("neo4j_connected"):
            pytest.skip("Neo4j not available")
        yield client


def test_check_known_interaction(int_client):
    resp = int_client.post(
        "/api/v1/interactions/check",
        json={
            "drugs": [{"name": "warfarin"}, {"name": "aspirin"}],
            "include_ml_prediction": False,
        },
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["highest_severity"] == "major"
    assert any(i["severity"] == "major" for i in body["interactions"])
