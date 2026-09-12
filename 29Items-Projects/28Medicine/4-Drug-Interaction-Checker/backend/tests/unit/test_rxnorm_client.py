"""Tests for the RxNorm HTTP client (mocked with respx)."""

import httpx
import pytest
import respx

from app.core.exceptions import RxNormUnavailableError
from app.integrations.rxnorm_client import RxNormClient

BASE = "https://rxnav.nlm.nih.gov/REST"


async def test_find_rxcui_by_name_found():
    with respx.mock:
        respx.get(f"{BASE}/rxcui.json").mock(
            return_value=httpx.Response(200, json={"idGroup": {"rxnormId": ["1191"]}})
        )
        assert await RxNormClient().find_rxcui_by_name("aspirin") == "1191"


async def test_find_rxcui_by_name_not_found():
    with respx.mock:
        respx.get(f"{BASE}/rxcui.json").mock(return_value=httpx.Response(200, json={"idGroup": {}}))
        assert await RxNormClient().find_rxcui_by_name("nope") is None


async def test_get_drug_properties():
    with respx.mock:
        respx.get(f"{BASE}/rxcui/1191/properties.json").mock(
            return_value=httpx.Response(200, json={"properties": {"name": "aspirin", "tty": "IN"}})
        )
        props = await RxNormClient().get_drug_properties("1191")
        assert props["name"] == "aspirin"


async def test_get_ingredients():
    payload = {
        "relatedGroup": {
            "conceptGroup": [
                {"tty": "IN", "conceptProperties": [{"rxcui": "1191", "name": "aspirin"}]}
            ]
        }
    }
    with respx.mock:
        respx.get(f"{BASE}/rxcui/1191/related.json").mock(
            return_value=httpx.Response(200, json=payload)
        )
        assert await RxNormClient().get_ingredients("1191") == [
            {"rxcui": "1191", "name": "aspirin"}
        ]


async def test_find_rxcui_by_ndc():
    with respx.mock:
        respx.get(f"{BASE}/ndcstatus.json").mock(
            return_value=httpx.Response(200, json={"ndcStatus": {"rxcui": "1191"}})
        )
        assert await RxNormClient().find_rxcui_by_ndc("0093-0123") == "1191"


async def test_http_error_raises_domain_error():
    with respx.mock:
        respx.get(f"{BASE}/rxcui.json").mock(return_value=httpx.Response(503))
        with pytest.raises(RxNormUnavailableError):
            await RxNormClient().find_rxcui_by_name("aspirin")
