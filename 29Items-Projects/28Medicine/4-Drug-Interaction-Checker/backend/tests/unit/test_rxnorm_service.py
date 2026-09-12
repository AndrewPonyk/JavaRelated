"""Unit tests for RxNormService (fake client, no network)."""

from app.services.rxnorm_service import RxNormService
from tests.fakes import FakeRxNormClient


async def test_normalize_name_found():
    svc = RxNormService(client=FakeRxNormClient())
    drug = await svc.normalize_name("aspirin")
    assert drug is not None
    assert drug.rxcui == "1191"
    assert drug.ingredients[0].name == "aspirin"


async def test_normalize_name_not_found():
    svc = RxNormService(client=FakeRxNormClient())
    assert await svc.normalize_name("not-a-real-drug") is None


async def test_resolve_by_rxcui():
    svc = RxNormService(client=FakeRxNormClient())
    drug = await svc.resolve(rxcui="1191")
    assert drug is not None and drug.rxcui == "1191"


async def test_resolve_by_ndc():
    svc = RxNormService(client=FakeRxNormClient())
    drug = await svc.resolve(ndc="good-ndc")
    assert drug is not None and drug.rxcui == "1191"


async def test_resolve_by_ndc_unknown():
    svc = RxNormService(client=FakeRxNormClient())
    assert await svc.resolve(ndc="bad-ndc") is None


async def test_resolve_by_name():
    svc = RxNormService(client=FakeRxNormClient())
    drug = await svc.resolve(name="aspirin")
    assert drug is not None and drug.rxcui == "1191"


async def test_resolve_with_nothing_returns_none():
    svc = RxNormService(client=FakeRxNormClient())
    assert await svc.resolve() is None


async def test_get_drug_is_cached():
    client = FakeRxNormClient()
    svc = RxNormService(client=client)
    await svc.get_drug("1191")
    await svc.get_drug("1191")
    assert client.props_calls == 1  # second call served from cache
