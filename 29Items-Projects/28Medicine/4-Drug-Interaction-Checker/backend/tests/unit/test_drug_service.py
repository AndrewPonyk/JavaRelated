"""Unit tests for DrugService (fake repo + fake rxnorm)."""

import pytest

from app.core.exceptions import DrugNotFoundError
from app.models.drug import Drug, DrugClassUpsert, DrugUpsert, IngredientUpsert
from app.services.drug_service import DrugService


class FakeRepo:
    def __init__(self, *, drug=None, search=None, listing=None):
        self._drug = drug
        self._search = search if search is not None else []
        self._listing = listing if listing is not None else ([], 0)
        self.created = None
        self.cls = None
        self.deleted = None

    async def get_drug(self, rxcui):
        return self._drug

    async def search_by_name(self, query_text, limit=10):
        return self._search

    async def list_drugs(self, limit=50, offset=0):
        return self._listing

    async def upsert_drug_full(self, payload):
        self.created = payload

    async def upsert_class(self, class_id, name, class_type=None):
        self.cls = (class_id, name, class_type)

    async def delete_drug(self, rxcui):
        self.deleted = rxcui
        return True


class FakeRx:
    def __init__(self, drug=None):
        self._drug = drug

    async def get_drug(self, rxcui):
        return self._drug

    async def normalize_name(self, name):
        return self._drug


async def test_get_from_graph():
    row = {
        "rxcui": "1",
        "name": "aspirin",
        "tty": "IN",
        "ingredients": [{"rxcui": "1", "name": "aspirin"}],
        "drug_classes": ["NSAID"],
    }
    svc = DrugService(FakeRepo(drug=row), FakeRx())
    drug = await svc.get("1")
    assert drug.name == "aspirin"
    assert drug.ingredients[0].rxcui == "1"
    assert drug.drug_classes == ["NSAID"]


async def test_get_falls_back_to_rxnorm():
    svc = DrugService(FakeRepo(drug=None), FakeRx(drug=Drug(rxcui="2", name="ibuprofen")))
    drug = await svc.get("2")
    assert drug.name == "ibuprofen"


async def test_get_not_found_raises():
    svc = DrugService(FakeRepo(drug=None), FakeRx(drug=None))
    with pytest.raises(DrugNotFoundError):
        await svc.get("zzz")


async def test_search_graph_first():
    svc = DrugService(FakeRepo(search=[{"rxcui": "1", "name": "aspirin", "tty": "IN"}]), FakeRx())
    result = await svc.search("asp")
    assert result.matches[0].rxcui == "1"


async def test_search_falls_back_to_rxnorm():
    svc = DrugService(FakeRepo(search=[]), FakeRx(drug=Drug(rxcui="9", name="z")))
    result = await svc.search("z")
    assert result.matches[0].rxcui == "9"


async def test_search_no_results():
    svc = DrugService(FakeRepo(search=[]), FakeRx(drug=None))
    result = await svc.search("nothing")
    assert result.matches == []


async def test_create_returns_drug():
    row = {"rxcui": "1", "name": "a", "tty": None, "ingredients": [], "drug_classes": []}
    repo = FakeRepo(drug=row)
    svc = DrugService(repo, FakeRx())
    payload = DrugUpsert(
        rxcui="1",
        name="a",
        ingredients=[IngredientUpsert(rxcui="i1", name="ing", class_ids=["C1"])],
        class_ids=["C1"],
    )
    drug = await svc.create(payload)
    assert drug.rxcui == "1"
    assert repo.created is payload


async def test_list():
    repo = FakeRepo(listing=([{"rxcui": "1", "name": "a", "tty": None}], 1))
    svc = DrugService(repo, FakeRx())
    result = await svc.list(limit=10, offset=0)
    assert result.total == 1
    assert result.items[0].rxcui == "1"


async def test_delete():
    repo = FakeRepo()
    svc = DrugService(repo, FakeRx())
    assert await svc.delete("1") is True
    assert repo.deleted == "1"


async def test_upsert_class():
    repo = FakeRepo()
    svc = DrugService(repo, FakeRx())
    await svc.upsert_class(DrugClassUpsert(class_id="C1", name="NSAID", class_type="ATC"))
    assert repo.cls == ("C1", "NSAID", "ATC")
