"""Contract tests: sqlmapapi REST client (respx) — lifecycle + parsing."""

from __future__ import annotations

import httpx
import pytest
from app.services.sqlmap_client import SqlmapClient, SqlmapError

BASE = "http://sqlmap:8775"
_TASK = "abc123"


def _mock_lifecycle(respx_mock, terminated: bool = True, data: dict | None = None):
    respx_mock.get(f"{BASE}/task/new").mock(
        return_value=httpx.Response(200, json={"success": True, "taskid": _TASK})
    )
    respx_mock.post(f"{BASE}/option/{_TASK}/set").mock(
        return_value=httpx.Response(200, json={"success": True})
    )
    respx_mock.post(f"{BASE}/scan/{_TASK}/start").mock(
        return_value=httpx.Response(200, json={"success": True})
    )
    respx_mock.get(f"{BASE}/scan/{_TASK}/status").mock(
        return_value=httpx.Response(
            200,
            json={
                "status": "terminated" if terminated else "running",
                "returncode": 0 if terminated else None,
            },
        )
    )
    respx_mock.get(f"{BASE}/scan/{_TASK}/data").mock(
        return_value=httpx.Response(200, json=data or {})
    )
    respx_mock.get(f"{BASE}/task/{_TASK}/delete").mock(
        return_value=httpx.Response(200, json={"success": True})
    )


async def test_full_lifecycle_parses_detections(respx_mock):
    _mock_lifecycle(
        respx_mock,
        data={
            "data": [
                {
                    "url": "http://t/items?id=1",
                    "dbms": "MySQL",
                    "value": {"id": {"technique": "B", "payload": "(SELECT ...)"}},
                }
            ]
        },
    )
    findings = await SqlmapClient(BASE).run_scan("http://t/items?id=1", poll_seconds=0.01)
    assert len(findings) == 1
    f = findings[0]
    assert f.parameter == "id" and f.technique == "B" and f.dbms == "MySQL"
    assert f.raw_log["payload"] == "(SELECT ...)"
    # task cleanup always happens
    assert respx_mock.get(f"{BASE}/task/{_TASK}/delete").called


async def test_basic_auth_sent(respx_mock):
    _mock_lifecycle(respx_mock, data={})
    await SqlmapClient(BASE).run_scan("http://t/?x=1", poll_seconds=0.01)
    request = respx_mock.get(f"{BASE}/task/new").calls.last.request
    assert request.headers["authorization"].startswith("Basic ")


async def test_destroy_called_even_on_error(respx_mock):
    _mock_lifecycle(respx_mock)
    respx_mock.post(f"{BASE}/scan/{_TASK}/start").mock(
        return_value=httpx.Response(500, text="boom")
    )
    with pytest.raises(SqlmapError, match="500"):
        await SqlmapClient(BASE).run_scan("http://t/?x=1", poll_seconds=0.01)
    assert respx_mock.get(f"{BASE}/task/{_TASK}/delete").called


async def test_timeout_raises_after_cleanup(respx_mock):
    _mock_lifecycle(respx_mock, terminated=False)
    with pytest.raises(SqlmapError, match="timeout"):
        await SqlmapClient(BASE).run_scan("http://t/?x=1", poll_seconds=0.01, timeout=0.05)
    assert respx_mock.get(f"{BASE}/task/{_TASK}/delete").called


async def test_task_new_failure(respx_mock):
    respx_mock.get(f"{BASE}/task/new").mock(
        return_value=httpx.Response(200, json={"success": False})
    )
    with pytest.raises(SqlmapError, match="task/new"):
        await SqlmapClient(BASE).run_scan("http://t/?x=1")


def test_parse_data_tolerant():
    assert SqlmapClient._parse_data({}) == []
    assert SqlmapClient._parse_data({"data": [{"url": "u", "value": None}]}) == []
    findings = SqlmapClient._parse_data(
        {
            "data": [
                {
                    "url": "u",
                    "dbms": None,
                    "value": {"p": {}},
                }
            ]
        }
    )
    assert findings[0].dbms is None and findings[0].technique is None
