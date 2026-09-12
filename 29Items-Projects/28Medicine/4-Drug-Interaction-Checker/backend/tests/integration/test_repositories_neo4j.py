"""Integration tests against a real Neo4j (CI seeds the graph first).

Run: pytest -m integration
"""

import pytest
import pytest_asyncio

from app.db import neo4j_client as nc

pytestmark = pytest.mark.integration


@pytest_asyncio.fixture
async def driver():
    nc._driver = None
    try:
        d = await nc.init_driver()
        ok = await nc.verify_connectivity()
    except Exception:
        ok = False
    if not ok:
        pytest.skip("Neo4j not available")
    yield d
    await nc.close_driver()


async def test_find_seeded_interaction(driver):
    from app.db.repositories.interaction_repository import InteractionRepository

    repo = InteractionRepository(driver)
    # warfarin (11289) + aspirin (1191) is seeded as a 'major' interaction.
    rows = await repo.find_interactions_for_set(["11289", "1191"])
    assert any(r["severity"] == "major" for r in rows)


async def test_crud_roundtrip(driver):
    from app.db.repositories.drug_repository import DrugRepository
    from app.db.repositories.interaction_repository import InteractionRepository
    from app.models.drug import DrugUpsert, IngredientUpsert

    drugs = DrugRepository(driver)
    interactions = InteractionRepository(driver)

    await drugs.upsert_drug_full(
        DrugUpsert(
            rxcui="TEST-DRUG-A",
            name="test-drug-a",
            ingredients=[IngredientUpsert(rxcui="TEST-ING-A", name="test-ing-a")],
        )
    )
    await drugs.upsert_drug_full(
        DrugUpsert(
            rxcui="TEST-DRUG-B",
            name="test-drug-b",
            ingredients=[IngredientUpsert(rxcui="TEST-ING-B", name="test-ing-b")],
        )
    )
    written = await interactions.upsert_interaction(
        "TEST-ING-A", "TEST-ING-B", severity="moderate", source="test"
    )
    assert written is True

    found = await interactions.find_interaction("TEST-ING-A", "TEST-ING-B")
    assert found is not None and found["severity"] == "moderate"

    assert await interactions.delete_interaction("TEST-ING-A", "TEST-ING-B") is True
    assert await drugs.delete_drug("TEST-DRUG-A") is True
    assert await drugs.delete_drug("TEST-DRUG-B") is True
