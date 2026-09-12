"""Unit tests for repositories using a fake Neo4j driver."""

from app.db.repositories.drug_repository import DrugRepository
from app.db.repositories.interaction_repository import InteractionRepository
from app.models.drug import DrugUpsert, IngredientUpsert
from tests.fakes import FakeDriver, FakeRecord, FakeResult


# ---- DrugRepository ----------------------------------------------------------
async def test_get_drug_found():
    record = FakeRecord(
        {"rxcui": "1", "name": "aspirin", "tty": "IN", "ingredients": [], "drug_classes": []}
    )
    repo = DrugRepository(FakeDriver(FakeResult(single=record)))
    assert (await repo.get_drug("1"))["name"] == "aspirin"


async def test_get_drug_missing():
    repo = DrugRepository(FakeDriver(FakeResult(single=None)))
    assert await repo.get_drug("x") is None


async def test_get_ingredient_rxcuis():
    record = FakeRecord({"rxcuis": ["1", "2"]})
    repo = DrugRepository(FakeDriver(FakeResult(single=record)))
    assert await repo.get_ingredient_rxcuis("d") == ["1", "2"]


async def test_search_by_name():
    record = FakeRecord({"rxcui": "1", "name": "aspirin", "tty": "IN"})
    repo = DrugRepository(FakeDriver(FakeResult(records=[record])))
    results = await repo.search_by_name("asp")
    assert results[0]["rxcui"] == "1"


async def test_search_by_name_empty_query_skips_db():
    driver = FakeDriver(FakeResult(records=[]))
    repo = DrugRepository(driver)
    assert await repo.search_by_name("!!!") == []
    assert driver.sessions == []  # no DB call made


async def test_list_drugs():
    data = FakeResult(records=[FakeRecord({"rxcui": "1", "name": "a", "tty": None})])
    count = FakeResult(single=FakeRecord({"total": 1}))
    repo = DrugRepository(FakeDriver([data, count]))
    items, total = await repo.list_drugs()
    assert total == 1 and items[0]["rxcui"] == "1"


async def test_upsert_drug_full_runs_all_statements():
    driver = FakeDriver(FakeResult())
    repo = DrugRepository(driver)
    payload = DrugUpsert(
        rxcui="1",
        name="a",
        class_ids=["C1"],
        ingredients=[IngredientUpsert(rxcui="i1", name="ing", class_ids=["C2"])],
    )
    await repo.upsert_drug_full(payload)
    # drug + class link + ingredient + drug-ingredient link + ingredient-class link
    assert len(driver.sessions[0].runs) >= 5


async def test_delete_drug_true_false():
    repo_true = DrugRepository(FakeDriver(FakeResult(single=FakeRecord({"found": 1}))))
    assert await repo_true.delete_drug("1") is True
    repo_false = DrugRepository(FakeDriver(FakeResult(single=None)))
    assert await repo_false.delete_drug("x") is False


async def test_upsert_drug_simple():
    driver = FakeDriver(FakeResult())
    repo = DrugRepository(driver)
    await repo.upsert_drug("1", "a", "IN")
    await repo.upsert_class("C1", "NSAID", "ATC")
    assert driver.sessions  # statements executed


# ---- InteractionRepository ---------------------------------------------------
def _interaction_record():
    return FakeRecord(
        {
            "rxcui_a": "1",
            "name_a": "a",
            "rxcui_b": "2",
            "name_b": "b",
            "severity": "major",
            "mechanism": "m",
            "evidence_level": "study",
            "description": "d",
            "source": "seed",
        }
    )


async def test_find_interaction():
    repo = InteractionRepository(FakeDriver(FakeResult(single=_interaction_record())))
    assert (await repo.find_interaction("1", "2"))["severity"] == "major"


async def test_find_interactions_for_set():
    repo = InteractionRepository(FakeDriver(FakeResult(records=[_interaction_record()])))
    assert len(await repo.find_interactions_for_set(["1", "2"])) == 1


async def test_find_interactions_for_set_too_few():
    repo = InteractionRepository(FakeDriver(FakeResult(records=[])))
    assert await repo.find_interactions_for_set(["1"]) == []


async def test_list_interactions():
    data = FakeResult(records=[_interaction_record()])
    count = FakeResult(single=FakeRecord({"total": 1}))
    repo = InteractionRepository(FakeDriver([data, count]))
    items, total = await repo.list_interactions()
    assert total == 1 and items[0]["rxcui_a"] == "1"


async def test_upsert_interaction_true_false():
    ok = InteractionRepository(FakeDriver(FakeResult(single=FakeRecord({"written": 1}))))
    assert await ok.upsert_interaction("2", "1", severity="major") is True
    missing = InteractionRepository(FakeDriver(FakeResult(single=None)))
    assert await missing.upsert_interaction("2", "1", severity="major") is False


async def test_delete_interaction_true_false():
    ok = InteractionRepository(FakeDriver(FakeResult(single=FakeRecord({"found": 1}))))
    assert await ok.delete_interaction("1", "2") is True
    missing = InteractionRepository(FakeDriver(FakeResult(single=None)))
    assert await missing.delete_interaction("1", "2") is False
