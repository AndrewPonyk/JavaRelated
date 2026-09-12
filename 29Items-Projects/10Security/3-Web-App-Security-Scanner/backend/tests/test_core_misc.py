"""Unit tests: metrics registry, log redaction, webhook delivery, retention."""

from __future__ import annotations

from datetime import timedelta

import httpx
from app.core.config import settings
from app.core.logging import redact
from app.core.metrics import metrics
from app.db.base import utcnow
from app.models.finding import Finding, Severity, Source
from app.services.retention import purge_expired_evidence
from app.services.webhooks import send_scan_webhook


class TestMetrics:
    def test_counters_render_prometheus_text(self):
        metrics.count("test_counter_total", labels={"kind": "unit"})
        metrics.observe("test_seconds", 0.25, labels={"kind": "unit"})
        text = metrics.render()
        assert "# TYPE test_counter_total counter" in text
        assert 'test_counter_total{kind="unit"}' in text
        assert "# TYPE test_seconds summary" in text
        assert 'test_seconds_count{kind="unit"} 1' in text

    def test_renders_pre_registered_zero_series(self):
        text = metrics.render()
        assert 'findings_total{severity="info"}' in text
        assert 'scanner_phase_seconds_count{phase="xss"}' in text


class TestGzipCompression:
    async def test_large_responses_compressed(self, client):
        for i in range(50):  # pad /metrics past the 1 KiB threshold
            metrics.count("test_gzip_fill_total", labels={"i": str(i)})
        resp = await client.get("/metrics", headers={"Accept-Encoding": "gzip"})
        assert resp.status_code == 200
        assert resp.headers.get("content-encoding") == "gzip"
        assert "findings_total" in resp.text  # httpx transparently decompresses

    async def test_small_responses_not_compressed(self, client):
        resp = await client.get("/health", headers={"Accept-Encoding": "gzip"})
        assert resp.status_code == 200
        assert resp.headers.get("content-encoding") is None


class TestRedaction:
    def test_authorization_stripped(self):
        assert "Bearer" not in redact("authorization: Bearer abc.def.ghi")

    def test_password_stripped(self):
        out = redact('body: {"password": "hunter2"}')
        assert "hunter2" not in out
        assert "[REDACTED]" in out

    def test_set_cookie_stripped(self):
        assert "SESSIONID=xyz" not in redact("Set-Cookie: SESSIONID=xyz; Path=/")

    def test_plain_text_untouched(self):
        assert redact("GET /api/v1/scans 201") == "GET /api/v1/scans 201"


class TestWebhook:
    async def test_disabled_is_noop(self):
        settings.SCAN_WEBHOOK_URL = ""
        assert await send_scan_webhook({"scan_id": 1}) is False

    async def test_success(self, respx_mock):
        settings.SCAN_WEBHOOK_URL = "https://hooks.example.com/x"
        route = respx_mock.post("https://hooks.example.com/x").mock(
            return_value=httpx.Response(200)
        )
        assert await send_scan_webhook({"scan_id": 7, "status": "completed"}) is True
        assert route.called
        body = route.calls.last.request.read()
        assert b"Scan 7" in body  # Slack text summary present

    async def test_http_error_returns_false(self, respx_mock):
        settings.SCAN_WEBHOOK_URL = "https://hooks.example.com/x"
        respx_mock.post("https://hooks.example.com/x").mock(return_value=httpx.Response(500))
        assert await send_scan_webhook({"scan_id": 7}) is False

    async def test_network_error_returns_false(self, respx_mock):
        settings.SCAN_WEBHOOK_URL = "https://hooks.example.com/x"
        respx_mock.post("https://hooks.example.com/x").mock(side_effect=httpx.ConnectError("nope"))
        assert await send_scan_webhook({"scan_id": 7}) is False


class TestRetention:
    async def test_old_evidence_purged_recent_kept(self, db_session):
        old = Finding(
            scan_id=1,
            source=Source.ZAP,
            rule_id="r",
            title="old",
            url="http://x",
            severity=Severity.LOW,
            dedup_hash="h1",
            evidence={"payload": "secret-old"},
            raw={"a": 1},
            created_at=utcnow() - timedelta(days=91),
        )
        recent = Finding(
            scan_id=1,
            source=Source.ZAP,
            rule_id="r",
            title="new",
            url="http://x",
            severity=Severity.LOW,
            dedup_hash="h2",
            evidence={"payload": "secret-new"},
            raw={"b": 2},
        )
        db_session.add_all([old, recent])
        await db_session.commit()

        purged = await purge_expired_evidence(db_session)
        assert purged == 1
        await db_session.refresh(old)
        await db_session.refresh(recent)
        assert old.evidence == {} and old.raw == {}
        assert recent.evidence == {"payload": "secret-new"}
