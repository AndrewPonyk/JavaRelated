"""Tests: scan orchestrator — full pipeline over mocked scanners (respx)."""

from __future__ import annotations

import asyncio
import contextlib
import json

import httpx
import pytest
from app.core.config import settings
from app.models.finding import Finding, Severity, Source
from app.models.scan import Scan, ScanProfile, ScanStatus
from app.schemas.scan import ScanProgress
from app.services.scan_orchestrator import ScanOrchestrator
from sqlalchemy import select

ZAP = "http://zap:8080"
SQLMAP = "http://sqlmap:8775"
TARGET = "http://t.example.com/"
SEARCH = "http://t.example.com/search?q=1"

ALERT = {
    "pluginId": "40018",
    "name": "SQL Injection",
    "risk": "high",
    "url": "http://t.example.com/items?id=1",
    "param": "id",
    "method": "GET",
    "cweid": "89",
    "description": "injection possible",
    "evidence": "OR 1=1",
}


def _mock_zap(
    respx_mock,
    alerts: list[dict] | None = None,
    discovered: list[str] | None = None,
    fail: bool = False,
):
    if fail:
        respx_mock.get(f"{ZAP}/JSON/spider/action/scan").mock(
            return_value=httpx.Response(200, json={"code": "scan_not_found", "message": "nope"})
        )
        respx_mock.get(f"{ZAP}/JSON/core/view/alerts").mock(
            return_value=httpx.Response(200, json={"alerts": []})
        )
        return
    respx_mock.get(f"{ZAP}/JSON/spider/action/scan").mock(
        return_value=httpx.Response(200, json={"scan": "5"})
    )
    respx_mock.get(f"{ZAP}/JSON/spider/view/status").mock(
        return_value=httpx.Response(200, json={"status": "100"})
    )
    respx_mock.get(f"{ZAP}/JSON/spider/view/results").mock(
        return_value=httpx.Response(200, json={"results": discovered or []})
    )
    respx_mock.get(f"{ZAP}/JSON/ascan/action/scan").mock(
        return_value=httpx.Response(200, json={"scan": "6"})
    )
    respx_mock.get(f"{ZAP}/JSON/ascan/view/status").mock(
        return_value=httpx.Response(200, json={"status": "100"})
    )
    respx_mock.get(f"{ZAP}/JSON/core/view/alerts").mock(
        return_value=httpx.Response(200, json={"alerts": alerts if alerts is not None else []})
    )


def _mock_sqlmap(respx_mock, data: dict | None = None):
    respx_mock.get(f"{SQLMAP}/task/new").mock(
        return_value=httpx.Response(200, json={"success": True, "taskid": "t1"})
    )
    respx_mock.post(f"{SQLMAP}/option/t1/set").mock(
        return_value=httpx.Response(200, json={"success": True})
    )
    respx_mock.post(f"{SQLMAP}/scan/t1/start").mock(
        return_value=httpx.Response(200, json={"success": True})
    )
    respx_mock.get(f"{SQLMAP}/scan/t1/status").mock(
        return_value=httpx.Response(200, json={"status": "terminated", "returncode": 0})
    )
    respx_mock.get(f"{SQLMAP}/scan/t1/data").mock(return_value=httpx.Response(200, json=data or {}))
    respx_mock.get(f"{SQLMAP}/task/t1/delete").mock(
        return_value=httpx.Response(200, json={"success": True})
    )


def _mock_xss(respx_mock):
    """The search URL echoes its q param raw (with a secret nearby to prove
    redaction); everything else is static."""

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/search":
            return httpx.Response(
                200, text=f"<div>password=hunter2 {request.url.params.get('q')}</div>"
            )
        return httpx.Response(200, text="<p>static page</p>")

    respx_mock.route().mock(side_effect=handler)


@pytest.fixture
def wired(session_factory, monkeypatch):
    """Point the orchestrator's DB + webhook at test infra."""
    monkeypatch.setattr("app.services.scan_orchestrator.SessionFactory", session_factory)
    monkeypatch.setattr(settings, "SCAN_WEBHOOK_URL", "")
    return ScanOrchestrator()


async def make_scan(session_factory, **kw) -> Scan:
    defaults = dict(target_url=TARGET, profile=ScanProfile.STANDARD, status=ScanStatus.PENDING)
    defaults.update(kw)
    async with session_factory() as session:
        scan = Scan(**defaults)
        session.add(scan)
        await session.commit()
        await session.refresh(scan)
        return scan


async def load_findings(session_factory, scan_id: int) -> list[Finding]:
    async with session_factory() as session:
        rows = (await session.scalars(select(Finding).where(Finding.scan_id == scan_id))).all()
        return list(rows)


async def test_full_pipeline_ingests_all_sources(wired, session_factory, respx_mock):
    _mock_zap(
        respx_mock, alerts=[ALERT, dict(ALERT)], discovered=[SEARCH, "http://t.example.com/about"]
    )
    _mock_sqlmap(
        respx_mock,
        data={
            "data": [
                {
                    "url": "http://t.example.com/items?id=1",
                    "dbms": "SQLite",
                    "value": {"id": {"technique": "U"}},
                }
            ]
        },
    )
    _mock_xss(respx_mock)
    scan = await make_scan(session_factory)

    await wired._run(scan.id)

    async with session_factory() as session:
        stored = await session.get(Scan, scan.id)
    assert stored.status == ScanStatus.COMPLETED
    assert stored.started_at and stored.finished_at
    assert stored.error_detail is None
    assert wired._progress[scan.id].phase == "done"

    findings = await load_findings(session_factory, scan.id)
    by_source = {f.source: f for f in findings}
    assert len(findings) == 3  # zap deduped (2 identical → 1)
    assert set(by_source) == {Source.ZAP, Source.SQLMAP, Source.XSS_ENGINE}

    zap_f = by_source[Source.ZAP]
    assert zap_f.severity == Severity.HIGH  # rules baseline: 40018 → high
    assert zap_f.owasp_category == "A03:2021"  # plugin mapping applied
    assert zap_f.cwe_id == "CWE-89"
    assert zap_f.evidence["excerpt"] == "OR 1=1"

    sqli = by_source[Source.SQLMAP]
    assert sqli.severity == Severity.CRITICAL  # union technique
    assert sqli.param == "id"
    assert sqli.evidence["dbms"] == "SQLite" and sqli.evidence["technique"] == "U"

    xss_f = by_source[Source.XSS_ENGINE]
    assert xss_f.param == "q"
    assert "hunter2" not in json.dumps(xss_f.evidence)  # redacted
    assert "[REDACTED]" in xss_f.evidence["response_excerpt"]


async def test_phase_failure_degrades_but_marks_failed(wired, session_factory, respx_mock):
    _mock_zap(respx_mock, fail=True)  # ZAP dies entirely
    _mock_sqlmap(respx_mock)  # sqlmap still healthy
    _mock_xss(respx_mock)  # XSS over seed URL still runs
    scan = await make_scan(session_factory)

    await wired._run(scan.id)

    async with session_factory() as session:
        stored = await session.get(Scan, scan.id)
    assert stored.status == ScanStatus.FAILED
    detail = json.loads(stored.error_detail)
    assert detail["phase"] == "zap"
    assert "zap" in detail["failed_phases"]
    # findings from surviving phases are still queryable
    findings = await load_findings(session_factory, scan.id)
    assert {f.source for f in findings} <= {Source.XSS_ENGINE}


async def test_fast_profile_skips_sqlmap(wired, session_factory, respx_mock):
    _mock_zap(respx_mock)
    _mock_xss(respx_mock)
    scan = await make_scan(session_factory, profile=ScanProfile.FAST)

    await wired._run(scan.id)
    assert not respx_mock.get(f"{SQLMAP}/task/new").called


async def test_webhook_fires_on_completion(wired, session_factory, respx_mock, monkeypatch):
    monkeypatch.setattr(settings, "SCAN_WEBHOOK_URL", "https://hooks.example/ci")
    hook = respx_mock.post("https://hooks.example/ci").mock(return_value=httpx.Response(200))
    _mock_zap(respx_mock, alerts=[ALERT])
    _mock_sqlmap(respx_mock)
    _mock_xss(respx_mock)
    scan = await make_scan(session_factory)

    await wired._run(scan.id)

    assert hook.called
    body = json.loads(hook.calls.last.request.read())
    assert body["scan_id"] == scan.id
    assert body["status"] == "completed"
    assert body["severity_counts"].get("high", 0) >= 1


async def test_progress_counts_findings_from_db(wired, session_factory):
    scan = await make_scan(session_factory)
    wired._progress[scan.id] = ScanProgress(scan_id=scan.id, phase="sqlmap", percent=45)
    async with session_factory() as session:
        for i in range(2):
            session.add(
                Finding(
                    scan_id=scan.id,
                    source=Source.ZAP,
                    rule_id=f"r{i}",
                    title=f"t{i}",
                    url=TARGET,
                    severity=Severity.LOW,
                    dedup_hash=f"h{i}",
                )
            )
        await session.commit()
        snapshot = await wired.progress(scan.id, session)
    assert snapshot.findings_so_far == 2
    assert snapshot.phase == "sqlmap"


async def test_progress_for_scan_never_dispatched(wired, session_factory):
    scan = await make_scan(session_factory, status=ScanStatus.PENDING)
    async with session_factory() as session:
        snapshot = await wired.progress(scan.id, session)
    assert snapshot.phase == "queued"

    done = await make_scan(session_factory, status=ScanStatus.COMPLETED)
    async with session_factory() as session:
        snapshot = await wired.progress(done.id, session)
    assert snapshot.phase == "done" and snapshot.percent == 100


async def test_cancel_stops_own_zap_jobs_only(wired, session_factory, respx_mock):
    stop_spider = respx_mock.get(f"{ZAP}/JSON/spider/action/stop").mock(
        return_value=httpx.Response(200, json={"Result": "ok"})
    )
    stop_ascan = respx_mock.get(f"{ZAP}/JSON/ascan/action/stop").mock(
        return_value=httpx.Response(200, json={"Result": "ok"})
    )
    sleeper = asyncio.ensure_future(asyncio.sleep(30))
    wired._running[1] = sleeper
    wired._zap_jobs[1] = {"spider": 5, "ascan": 6}

    await wired.cancel(1)

    assert sleeper.cancelling() == 1
    assert stop_spider.called and stop_ascan.called
    assert 1 not in wired._zap_jobs
    with contextlib.suppress(asyncio.CancelledError):
        await sleeper  # reap the cancelled task (no pending-task warnings)


async def test_run_guarded_marks_failed_on_crash(wired, session_factory, monkeypatch):
    scan = await make_scan(session_factory)

    async def _boom(scan_id):
        raise RuntimeError("kaboom")

    monkeypatch.setattr(wired, "_run", _boom)
    await wired._run_guarded(scan.id)
    async with session_factory() as session:
        stored = await session.get(Scan, scan.id)
    assert stored.status == ScanStatus.FAILED
    assert "kaboom" in stored.error_detail


class TestSelectXssTargets:
    def test_params_first_dedup_cap(self):
        discovered = [
            "http://t/plain1",
            "http://t/s?x=1",
            "http://t/s?y=2",
            TARGET,
            "http://t/plain2",
        ] + [f"http://t/p{i}?v={i}" for i in range(20)]
        picked = ScanOrchestrator._select_xss_targets(TARGET, discovered)
        assert picked[0] == TARGET  # seed always present
        assert picked[1] == "http://t/s?x=1"  # parameterized first
        assert len(picked) == 12  # hard cap
        assert len(set(picked)) == len(picked)  # no duplicates
