"""Unit tests for InteractionService (fakes, no DB/network)."""

import pytest

from app.core import config
from app.core.exceptions import DrugNotFoundError, TooManyDrugsError
from app.models.common import Severity
from app.models.drug import Drug, Ingredient
from app.models.interaction import (
    InteractionCheckRequest,
    InteractionPair,
    InteractionUpsert,
)
from app.services.interaction_service import InteractionService
from app.services.ml_severity_service import SeverityPrediction


def _drug(rxcui, name, ing_rxcui, ing_name):
    return Drug(rxcui=rxcui, name=name, ingredients=[Ingredient(rxcui=ing_rxcui, name=ing_name)])


class FakeInteractionRepo:
    def __init__(self, known=None, *, interaction=None, listing=None, written=True, deleted=True):
        self._known = known or []
        self._interaction = interaction
        self._listing = listing if listing is not None else ([], 0)
        self._written = written
        self._deleted = deleted

    async def find_interactions_for_set(self, rxcuis):
        return self._known

    async def upsert_interaction(self, a, b, **kwargs):
        return self._written

    async def find_interaction(self, a, b):
        return self._interaction

    async def list_interactions(self, limit=50, offset=0):
        return self._listing

    async def delete_interaction(self, a, b):
        return self._deleted


class FakeML:
    def __init__(self, preds=None):
        self._preds = preds or {}

    async def predict_batch(self, pairs):
        return {p: self._preds[p] for p in pairs if p in self._preds}


class FakeRx:
    def __init__(self, mapping):
        self._mapping = mapping

    async def resolve(self, rxcui=None, ndc=None, name=None):
        return self._mapping.get(name or rxcui or ndc)


def _service(known=None, preds=None, rx=None, repo=None):
    return InteractionService(
        repo or FakeInteractionRepo(known),
        drug_repo=None,
        rxnorm=rx or FakeRx({}),
        ml=FakeML(preds),
    )


# ---- helpers -----------------------------------------------------------------
def test_highest_severity_empty():
    assert InteractionService._highest_severity([]) is Severity.UNKNOWN


def test_highest_severity_picks_max():
    pairs = [
        InteractionPair(rxcui_a="1", rxcui_b="2", name_a="a", name_b="b", severity=Severity.MINOR),
        InteractionPair(rxcui_a="1", rxcui_b="3", name_a="a", name_b="c", severity=Severity.MAJOR),
    ]
    assert InteractionService._highest_severity(pairs) is Severity.MAJOR


def test_ingredient_index_uses_ingredients_then_drug_fallback():
    resolved = {
        "100": _drug("100", "combo", "1", "ing-a"),
        "200": Drug(rxcui="200", name="mono", ingredients=[]),
    }
    assert InteractionService._ingredient_index(resolved) == {"1": "ing-a", "200": "mono"}


# ---- check() -----------------------------------------------------------------
async def test_check_known_interaction():
    rx = FakeRx(
        {
            "warf": _drug("dA", "WarfarinTab", "i1", "warfarin"),
            "asp": _drug("dB", "AspirinTab", "i2", "aspirin"),
        }
    )
    known = [
        {
            "rxcui_a": "i1",
            "name_a": "warfarin",
            "rxcui_b": "i2",
            "name_b": "aspirin",
            "severity": "major",
            "mechanism": "bleeding",
            "evidence_level": "established",
            "description": "risk",
            "source": "seed",
        }
    ]
    svc = _service(known=known, rx=rx)
    req = InteractionCheckRequest(
        drugs=[{"name": "warf"}, {"name": "asp"}], include_ml_prediction=False
    )
    resp = await svc.check(req)
    assert resp.highest_severity == Severity.MAJOR
    assert len(resp.interactions) == 1
    assert resp.interactions[0].evidence_level.value == "established"
    assert resp.unresolved == []


async def test_check_with_ml_prediction():
    rx = FakeRx({"a": _drug("dA", "A", "i1", "ing1"), "b": _drug("dB", "B", "i2", "ing2")})
    preds = {("i1", "i2"): SeverityPrediction(severity=Severity.MODERATE, confidence=0.77)}
    svc = _service(known=[], preds=preds, rx=rx)
    req = InteractionCheckRequest(drugs=[{"name": "a"}, {"name": "b"}], include_ml_prediction=True)
    resp = await svc.check(req)
    assert len(resp.interactions) == 1
    assert resp.interactions[0].ml_predicted is True
    assert resp.interactions[0].ml_confidence == 0.77


async def test_check_ml_skips_unknown_severity():
    rx = FakeRx({"a": _drug("dA", "A", "i1", "ing1"), "b": _drug("dB", "B", "i2", "ing2")})
    preds = {("i1", "i2"): SeverityPrediction(severity=Severity.UNKNOWN, confidence=0.2)}
    svc = _service(known=[], preds=preds, rx=rx)
    req = InteractionCheckRequest(drugs=[{"name": "a"}, {"name": "b"}], include_ml_prediction=True)
    resp = await svc.check(req)
    assert resp.interactions == []
    assert resp.highest_severity == Severity.UNKNOWN


async def test_check_unresolved_drugs():
    rx = FakeRx({"good": _drug("dA", "A", "i1", "ing1")})
    svc = _service(known=[], rx=rx)
    req = InteractionCheckRequest(
        drugs=[{"name": "good"}, {"name": "bogus"}], include_ml_prediction=False
    )
    resp = await svc.check(req)
    assert resp.unresolved == ["bogus"]
    assert resp.checked_drugs == ["A"]


async def test_check_caps_ml_pairs():
    settings = config.get_settings()
    original = settings.max_pairs_for_ml
    settings.max_pairs_for_ml = 0  # cap below the single unknown pair
    try:
        rx = FakeRx({"a": _drug("dA", "A", "i1", "ing1"), "b": _drug("dB", "B", "i2", "ing2")})
        preds = {("i1", "i2"): SeverityPrediction(severity=Severity.MAJOR, confidence=0.9)}
        svc = _service(known=[], preds=preds, rx=rx)
        req = InteractionCheckRequest(
            drugs=[{"name": "a"}, {"name": "b"}], include_ml_prediction=True
        )
        resp = await svc.check(req)
        assert resp.interactions == []  # capped -> no ML predictions made
    finally:
        settings.max_pairs_for_ml = original


async def test_check_too_many_drugs():
    settings = config.get_settings()
    original = settings.max_drugs_per_check
    settings.max_drugs_per_check = 1
    try:
        svc = _service(rx=FakeRx({}))
        req = InteractionCheckRequest(drugs=[{"name": "a"}, {"name": "b"}])
        with pytest.raises(TooManyDrugsError):
            await svc.check(req)
    finally:
        settings.max_drugs_per_check = original


# ---- admin CRUD --------------------------------------------------------------
async def test_create_interaction():
    interaction = {
        "rxcui_a": "1",
        "name_a": "a",
        "rxcui_b": "2",
        "name_b": "b",
        "severity": "major",
        "mechanism": None,
        "evidence_level": None,
        "description": None,
        "source": "manual",
    }
    repo = FakeInteractionRepo(written=True, interaction=interaction)
    svc = _service(repo=repo)
    pair = await svc.create_interaction(
        InteractionUpsert(rxcui_a="1", rxcui_b="2", severity=Severity.MAJOR)
    )
    assert pair.severity == Severity.MAJOR


async def test_create_interaction_missing_ingredient():
    repo = FakeInteractionRepo(written=False)
    svc = _service(repo=repo)
    with pytest.raises(DrugNotFoundError):
        await svc.create_interaction(
            InteractionUpsert(rxcui_a="1", rxcui_b="2", severity=Severity.MAJOR)
        )


async def test_list_interactions():
    row = {
        "rxcui_a": "1",
        "name_a": "a",
        "rxcui_b": "2",
        "name_b": "b",
        "severity": "minor",
        "mechanism": None,
        "evidence_level": None,
        "description": None,
        "source": "seed",
    }
    repo = FakeInteractionRepo(listing=([row], 1))
    svc = _service(repo=repo)
    result = await svc.list_interactions()
    assert result.total == 1
    assert result.items[0].rxcui_a == "1"


async def test_delete_interaction():
    svc = _service(repo=FakeInteractionRepo(deleted=True))
    assert await svc.delete_interaction("1", "2") is True
