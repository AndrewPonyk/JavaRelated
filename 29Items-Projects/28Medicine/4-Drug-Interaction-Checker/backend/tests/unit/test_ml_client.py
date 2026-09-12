"""Tests for the ML severity service client (mocked with respx)."""

import httpx
import respx

from app.core import config
from app.services.ml_severity_service import MLSeverityService

BASE = "http://localhost:8001"


async def test_predict_success():
    with respx.mock:
        respx.post(f"{BASE}/predict").mock(
            return_value=httpx.Response(200, json={"severity": "major", "confidence": 0.8})
        )
        pred = await MLSeverityService().predict("1", "2")
        assert pred is not None
        assert pred.severity.value == "major"
        assert pred.confidence == 0.8


async def test_predict_http_error_is_failsoft():
    with respx.mock:
        respx.post(f"{BASE}/predict").mock(return_value=httpx.Response(500))
        assert await MLSeverityService().predict("1", "2") is None


async def test_predict_batch_success():
    payload = {
        "predictions": [{"rxcui_a": "1", "rxcui_b": "2", "severity": "moderate", "confidence": 0.7}]
    }
    with respx.mock:
        respx.post(f"{BASE}/predict/batch").mock(return_value=httpx.Response(200, json=payload))
        out = await MLSeverityService().predict_batch([("1", "2")])
        assert out[("1", "2")].severity.value == "moderate"


async def test_predict_batch_empty_input():
    assert await MLSeverityService().predict_batch([]) == {}


async def test_predict_batch_http_error_is_failsoft():
    with respx.mock:
        respx.post(f"{BASE}/predict/batch").mock(return_value=httpx.Response(500))
        assert await MLSeverityService().predict_batch([("1", "2")]) == {}


async def test_disabled_service_returns_empty():
    settings = config.get_settings()
    original = settings.ml_enabled
    settings.ml_enabled = False
    try:
        service = MLSeverityService()
        assert await service.predict("1", "2") is None
        assert await service.predict_batch([("1", "2")]) == {}
    finally:
        settings.ml_enabled = original
