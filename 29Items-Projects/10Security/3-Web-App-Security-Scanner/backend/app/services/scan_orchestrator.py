"""Scan orchestrator — the state machine coordinating the scan pipeline.

Pipeline: ZAP (spider+active) → SQLMap → XSS engine → normalize → classify →
persist → webhook. Runs as a background asyncio task per scan; the logic is
framework-free below the dispatch line so it lifts into Celery unchanged.

Failure philosophy (ARCHITECTURE 2.6): a phase failure marks the scan FAILED
with machine-readable detail, but findings ingested from earlier phases
survive and remain queryable.
"""

from __future__ import annotations

import asyncio
import json
import logging
from collections.abc import Awaitable, Callable
from datetime import timedelta
from typing import Any
from urllib.parse import parse_qsl, urlsplit

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.logging import redact
from app.core.metrics import metrics, timer
from app.db.base import utcnow
from app.db.session import SessionFactory
from app.models.finding import Finding, Severity, Source
from app.models.scan import Scan, ScanProfile, ScanStatus
from app.schemas.scan import ScanProgress
from app.services.ml_classifier import classifier
from app.services.sqlmap_client import SqlmapClient, SqlmapFinding
from app.services.webhooks import send_scan_webhook
from app.services.xss_scanner import XssHit, XssScanner
from app.services.zap_client import ZapAlert, ZapClient, ZapError

logger = logging.getLogger(__name__)

_XSS_MAX_URLS = 12  # spider-discovered URLs probed by the XSS engine
_EVIDENCE_CAP = 2048  # max chars per stored evidence string

# OWASP Top 10 2021 mapping per ZAP plugin id (extended per-rule as tuned)
_ZAP_OWASP: dict[str, str] = {
    "40018": "A03:2021",
    "40019": "A03:2021",
    "40014": "A03:2021",
    "10038": "A05:2021",
    "10098": "A05:2021",
    "10021": "A05:2021",
    "10017": "A05:2021",
    "10106": "A02:2021",
    "10104": "A05:2021",
}
_RISK_TO_SEVERITY = {
    "informational": Severity.INFO,
    "low": Severity.LOW,
    "medium": Severity.MEDIUM,
    "high": Severity.HIGH,
}
_TECH_TO_SEVERITY = {
    "B": Severity.HIGH,  # boolean-blind
    "T": Severity.HIGH,  # time-blind
    "U": Severity.CRITICAL,  # union (data extraction)
    "E": Severity.CRITICAL,  # error-based (DBMS leaks)
}


def _capped(value: str | None) -> str | None:
    if value is None:
        return None
    return redact(value[:_EVIDENCE_CAP])


class ScanOrchestrator:
    def __init__(self) -> None:
        self._zap = ZapClient()
        self._sqlmap = SqlmapClient()
        self._xss = XssScanner()
        self._running: dict[int, asyncio.Task] = {}
        self._progress: dict[int, ScanProgress] = {}
        self._zap_jobs: dict[int, dict[str, int]] = {}  # per-scan spider/ascan ids
        self._gate = asyncio.Semaphore(settings.MAX_CONCURRENT_SCANS)

    # ── API surface ────────────────────────────────────────────────────

    async def dispatch(self, scan_id: int) -> None:
        """Launch scan in background; returns immediately (202-style)."""
        task = asyncio.create_task(self._run_guarded(scan_id))
        self._running[scan_id] = task

    async def progress(self, scan_id: int, session: AsyncSession) -> ScanProgress:
        if scan_id in self._progress:
            snap = self._progress[scan_id]
            if snap.phase in ("done", "failed"):
                return snap
            findings = await session.scalar(
                select(func.count(Finding.id)).where(Finding.scan_id == scan_id)
            )
            return snap.model_copy(update={"findings_so_far": findings or 0})
        scan = await session.get(Scan, scan_id)
        if scan is None:
            return ScanProgress(scan_id=scan_id, phase="failed", percent=0)
        if scan.status == ScanStatus.COMPLETED:
            return ScanProgress(scan_id=scan_id, phase="done", percent=100)
        if scan.status in (ScanStatus.FAILED, ScanStatus.CANCELLED):
            return ScanProgress(scan_id=scan_id, phase="failed", percent=0)
        return ScanProgress(scan_id=scan_id, phase="queued", percent=0)

    async def cancel(self, scan_id: int) -> None:
        if (task := self._running.get(scan_id)) and not task.done():
            task.cancel()
        jobs = self._zap_jobs.pop(scan_id, None)
        if jobs:
            try:
                if "spider" in jobs:
                    await self._zap.stop_spider(jobs["spider"])
                if "ascan" in jobs:
                    await self._zap.stop_active_scan(jobs["ascan"])
            except ZapError:
                logger.warning("zap per-scan stop failed for scan %s — stopping all", scan_id)
                await self._zap.stop_everything()

    # ── pipeline ───────────────────────────────────────────────────────

    async def _run_guarded(self, scan_id: int) -> None:
        async with self._gate:  # cap concurrent scans platform-wide
            try:
                await self._run(scan_id)
            except asyncio.CancelledError:
                await self._set_status(scan_id, ScanStatus.CANCELLED)
                raise
            except Exception as exc:
                logger.exception("scan %s failed", scan_id)
                await self._set_status(
                    scan_id,
                    ScanStatus.FAILED,
                    error_detail={"phase": "pipeline", "reason": str(exc)},
                )
            finally:
                self._running.pop(scan_id, None)
                self._zap_jobs.pop(scan_id, None)

    async def _run(self, scan_id: int) -> None:
        self._progress[scan_id] = ScanProgress(scan_id=scan_id, phase="spider", percent=0)
        async with SessionFactory() as session:
            scan = await session.get(Scan, scan_id)
            if scan is None:
                raise RuntimeError(f"scan {scan_id} vanished")
            scan.status = ScanStatus.RUNNING
            scan.started_at = utcnow()
            scan.timeout_at = utcnow() + timedelta(minutes=settings.SCAN_TIMEOUT_MINUTES)
            await session.commit()
            target, profile = scan.target_url, scan.profile

        deep = profile == ScanProfile.DEEP
        timeout = settings.SCAN_TIMEOUT_MINUTES * 60.0
        failures: list[tuple[str, str]] = []  # (phase, reason) — degrade, don't abort

        # Phase 1+2: ZAP spider & active scan (+ discovered URLs for XSS)
        discovered: list[str] = []
        try:
            with timer("scanner_phase_seconds", phase="zap_spider"):
                spider_id = await self._zap.start_spider(target)
                self._zap_jobs.setdefault(scan_id, {})["spider"] = spider_id
                await self._poll_until_done(
                    lambda: self._zap.spider_progress(spider_id), 5.0, timeout / 3
                )
                discovered = await self._zap.spider_results(spider_id)
            with timer("scanner_phase_seconds", phase="zap_active"):
                ascan_id = await self._zap.start_active_scan(target)
                self._zap_jobs[scan_id]["ascan"] = ascan_id
                await self._poll_until_done(
                    lambda: self._zap.active_scan_progress(ascan_id), 5.0, timeout / 2
                )
        except ZapError as exc:
            failures.append(("zap", str(exc)))
        try:
            alerts = await self._zap.alerts(target)
        except ZapError as exc:
            failures.append(("zap_alerts", str(exc)))
            alerts = []
        await self._ingest_zap(scan_id, alerts)
        self._progress[scan_id] = ScanProgress(scan_id=scan_id, phase="sqlmap", percent=45)

        # Phase 3: SQLMap — FAST skips injection testing
        if profile != ScanProfile.FAST:
            try:
                with timer("scanner_phase_seconds", phase="sqlmap"):
                    sqlmap_findings = await self._sqlmap.run_scan(
                        target, **({"risk": 2, "level": 3} if deep else {})
                    )
            except Exception as exc:
                failures.append(("sqlmap", str(exc)))
                sqlmap_findings = []
            await self._ingest_sqlmap(scan_id, sqlmap_findings)
        self._progress[scan_id] = ScanProgress(scan_id=scan_id, phase="xss", percent=70)

        # Phase 4: custom XSS engine over spider-discovered URLs (params first)
        xss_urls = self._select_xss_targets(target, discovered)
        try:
            with timer("scanner_phase_seconds", phase="xss"):
                xss_hits = await self._xss.probe_urls(xss_urls) if xss_urls else []
        except Exception as exc:
            failures.append(("xss", str(exc)))
            xss_hits = []
        await self._ingest_xss(scan_id, xss_hits)

        # Finish: final status reflects phase failures; findings all survive
        await self._finish(scan_id, target, profile, failures)

    @staticmethod
    def _select_xss_targets(target: str, discovered: list[str]) -> list[str]:
        """Parameterized URLs first (XSS needs params); always include the seed."""

        def _has_query(u: str) -> bool:
            return bool(parse_qsl(urlsplit(u).query))

        seen, ordered = {target}, [target]
        with_params = [u for u in discovered if _has_query(u)]
        without = [u for u in discovered if not _has_query(u)]
        for url in with_params + without:
            if url not in seen:
                seen.add(url)
                ordered.append(url)
        return ordered[:_XSS_MAX_URLS]

    # ── normalization & persistence ────────────────────────────────────

    async def _ingest_zap(self, scan_id: int, alerts: list[ZapAlert]) -> None:
        for alert in alerts:
            await self._persist_finding(
                scan_id,
                source=Source.ZAP.value,
                rule_id=alert.plugin_id,
                title=alert.name,
                url=alert.url,
                param=alert.param,
                method=alert.method,
                cwe_id=alert.cweid,
                base_severity=_RISK_TO_SEVERITY.get(alert.risk, Severity.INFO),
                owasp=_ZAP_OWASP.get(alert.plugin_id),
                description=alert.description,
                evidence={"excerpt": _capped(alert.evidence)},
                raw=alert.raw,
            )

    async def _ingest_sqlmap(self, scan_id: int, findings: list[SqlmapFinding]) -> None:
        for f in findings:
            technique_key = (f.technique or "?")[0].upper()
            await self._persist_finding(
                scan_id,
                source=Source.SQLMAP.value,
                rule_id="SQLI-CONFIRMED",
                title=f"SQL injection: parameter {f.parameter!r} ({f.technique or '?'})",
                url=f.url,
                param=f.parameter,
                method="GET",
                cwe_id="CWE-89",
                base_severity=_TECH_TO_SEVERITY.get(technique_key, Severity.HIGH),
                owasp="A03:2021",
                description=f"Confirmed via sqlmap (dbms={f.dbms or 'unknown'})",
                evidence={"technique": f.technique, "dbms": f.dbms},
                raw=f.raw_log,
            )

    async def _ingest_xss(self, scan_id: int, hits: list[XssHit]) -> None:
        for h in hits:
            await self._persist_finding(
                scan_id,
                source=Source.XSS_ENGINE.value,
                rule_id=f"XSS-{h.context.value.upper()}",
                title=f"Reflected XSS in {h.context.value} context (param {h.param!r})",
                url=h.url,
                param=h.param,
                method="GET",
                cwe_id="CWE-79",
                base_severity=Severity.HIGH,
                owasp="A03:2021",
                description=f"Payload reflected unescaped: {h.payload!r}",
                evidence={"payload": h.payload, "response_excerpt": _capped(h.evidence_excerpt)},
                raw={"payload": h.payload, "context": h.context.value},
            )

    async def _persist_finding(
        self,
        scan_id: int,
        *,
        source: str,
        rule_id: str,
        title: str,
        url: str,
        param: str | None,
        method: str | None,
        cwe_id: str | None,
        base_severity: Severity,
        owasp: str | None,
        description: str | None,
        evidence: dict[str, Any],
        raw: dict[str, Any],
    ) -> None:
        dedup = Finding.compute_dedup_hash(source, rule_id, url, param)
        result = classifier.classify(
            source=source,
            rule_id=rule_id,
            title=title,
            description=description or "",
            param=param,
            method=method,
        )
        async with SessionFactory() as session:
            exists = await session.scalar(
                select(Finding.id).where(Finding.scan_id == scan_id, Finding.dedup_hash == dedup)
            )
            if exists:
                return  # deduped — same rule+param already recorded
            session.add(
                Finding(
                    scan_id=scan_id,
                    source=Source(source),
                    rule_id=rule_id,
                    title=title[:512],
                    description=_capped(description),
                    url=url[:2048],
                    param=param,
                    method=method,
                    severity=result.severity,
                    severity_confidence=result.confidence,
                    owasp_category=owasp,
                    cwe_id=cwe_id,
                    dedup_hash=dedup,
                    evidence=evidence,
                    raw=raw,
                )
            )
            await session.commit()
            metrics.count("findings_total", labels={"severity": result.severity.value})

    # ── helpers ────────────────────────────────────────────────────────

    async def _set_status(
        self, scan_id: int, status: ScanStatus, error_detail: dict | None = None
    ) -> None:
        async with SessionFactory() as session:
            scan = await session.get(Scan, scan_id)
            if scan is None:
                return
            scan.status = status
            if status in {ScanStatus.COMPLETED, ScanStatus.FAILED, ScanStatus.CANCELLED}:
                scan.finished_at = utcnow()
            if error_detail:
                scan.error_detail = json.dumps(error_detail)
            await session.commit()

    async def _finish(
        self,
        scan_id: int,
        target: str,
        profile: ScanProfile,
        failures: list[tuple[str, str]],
    ) -> None:
        """Set the final status, emit metrics, and fire the webhook.

        A scan with any phase failure is FAILED (machine-readable detail in
        error_detail) but every finding ingested before the failure survives.
        """
        if failures:
            phase, reason = failures[0]
            logger.error("scan %s failed in phase %s: %s", scan_id, phase, reason)
            for failed_phase, _ in failures:
                metrics.count("scan_phase_failures_total", labels={"phase": failed_phase})
            await self._set_status(
                scan_id,
                ScanStatus.FAILED,
                error_detail={
                    "phase": phase,
                    "reason": reason[:500],
                    "failed_phases": [p for p, _ in failures],
                },
            )
            final_status = "failed"
            metrics.count("scans_total", labels={"status": "failed"})
        else:
            await self._set_status(scan_id, ScanStatus.COMPLETED)
            final_status = "completed"
            metrics.count("scans_total", labels={"status": "completed", "profile": profile.value})
        self._progress[scan_id] = ScanProgress(
            scan_id=scan_id,
            phase="done" if final_status == "completed" else "failed",
            percent=100 if final_status == "completed" else 0,
        )
        await self._notify(scan_id, target, final_status)

    async def _notify(self, scan_id: int, target: str, status: str) -> None:
        async with SessionFactory() as session:
            counts: dict[Severity, int] = {
                sev: n
                for sev, n in (
                    await session.execute(
                        select(Finding.severity, func.count(Finding.id))
                        .where(Finding.scan_id == scan_id)
                        .group_by(Finding.severity)
                    )
                ).all()
            }
        await send_scan_webhook(
            {
                "event": "scan.completed",
                "scan_id": scan_id,
                "target_url": target,
                "status": status,
                "severity_counts": {sev.value: n for sev, n in counts.items()},
                "high_or_critical": sum(
                    n for sev, n in counts.items() if sev in (Severity.HIGH, Severity.CRITICAL)
                ),
            }
        )

    async def _poll_until_done(
        self, get_percent: Callable[[], Awaitable[int]], poll_seconds: float, timeout: float
    ) -> None:
        elapsed = 0.0
        while elapsed < timeout:
            percent = await get_percent()
            if percent >= 100:
                return
            await asyncio.sleep(poll_seconds)
            elapsed += poll_seconds
        raise ZapError(f"ZAP phase exceeded timeout of {timeout:.0f}s")


orchestrator = ScanOrchestrator()
