"""Contract tests: ZAP REST client against a mocked /JSON API (respx)."""

from __future__ import annotations

import httpx
import pytest
from app.services.zap_client import ZapClient, ZapError

BASE = "http://zap:8080"


async def test_version(respx_mock, sample_alert_payload):
    respx_mock.get(f"{BASE}/JSON/core/view/version").mock(
        return_value=httpx.Response(200, json={"version": "2.17.0"})
    )
    assert await ZapClient(BASE).version() == "2.17.0"


async def test_alerts_normalized(respx_mock, sample_alert_payload):
    respx_mock.get(f"{BASE}/JSON/core/view/alerts").mock(
        return_value=httpx.Response(200, json={"alerts": [sample_alert_payload]})
    )
    alerts = await ZapClient(BASE).alerts("https://target.example.com")
    assert len(alerts) == 1
    alert = alerts[0]
    assert alert.plugin_id == "40018"
    assert alert.risk == "high"
    assert alert.cweid == "CWE-89"  # prefixed on normalize
    assert alert.param == "id"
    assert alert.raw == sample_alert_payload  # native payload preserved


async def test_error_envelope_raises(respx_mock):
    """ZAP returns HTTP 200 with {'code','message'} on API errors."""
    respx_mock.get(f"{BASE}/JSON/spider/action/scan").mock(
        return_value=httpx.Response(200, json={"code": "illegal_parameter", "message": "bad url"})
    )
    with pytest.raises(ZapError, match="bad url"):
        await ZapClient(BASE).start_spider("http://x")


async def test_http_error_raises(respx_mock):
    respx_mock.get(f"{BASE}/JSON/core/view/version").mock(
        return_value=httpx.Response(503, text="down")
    )
    with pytest.raises(ZapError, match="503"):
        await ZapClient(BASE).version()


async def test_spider_lifecycle_and_results(respx_mock):
    client = ZapClient(BASE)
    respx_mock.get(f"{BASE}/JSON/spider/action/scan").mock(
        return_value=httpx.Response(200, json={"scan": "3"})
    )
    respx_mock.get(f"{BASE}/JSON/spider/view/status").mock(
        return_value=httpx.Response(200, json={"status": "100"})
    )
    respx_mock.get(f"{BASE}/JSON/spider/view/results").mock(
        return_value=httpx.Response(200, json={"results": ["http://t/a", "http://t/b?q=1"]})
    )
    spider_id = await client.start_spider("http://t")
    assert spider_id == 3
    assert await client.spider_progress(spider_id) == 100
    assert await client.spider_results(spider_id) == ["http://t/a", "http://t/b?q=1"]


async def test_run_full_scan_composite(respx_mock):
    client = ZapClient(BASE)
    respx_mock.get(f"{BASE}/JSON/spider/action/scan").mock(
        return_value=httpx.Response(200, json={"scan": "0"})
    )
    respx_mock.get(f"{BASE}/JSON/spider/view/status").mock(
        return_value=httpx.Response(200, json={"status": "100"})
    )
    respx_mock.get(f"{BASE}/JSON/ascan/action/scan").mock(
        return_value=httpx.Response(200, json={"scan": "1"})
    )
    respx_mock.get(f"{BASE}/JSON/ascan/view/status").mock(
        return_value=httpx.Response(200, json={"status": "100"})
    )
    respx_mock.get(f"{BASE}/JSON/core/view/alerts").mock(
        return_value=httpx.Response(200, json={"alerts": []})
    )
    alerts = await client.run_full_scan("http://t", poll_seconds=0.01)
    assert alerts == []


async def test_timeout_raises(respx_mock):
    client = ZapClient(BASE)
    respx_mock.get(f"{BASE}/JSON/spider/action/scan").mock(
        return_value=httpx.Response(200, json={"scan": "0"})
    )
    respx_mock.get(f"{BASE}/JSON/spider/view/status").mock(
        return_value=httpx.Response(200, json={"status": "42"})
    )
    with pytest.raises(ZapError, match="timeout"):
        await client.run_full_scan("http://t", poll_seconds=0.01, timeout=0.05)
