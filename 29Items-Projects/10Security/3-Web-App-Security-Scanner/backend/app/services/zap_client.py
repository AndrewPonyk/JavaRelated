"""OWASP ZAP REST API client (async httpx).

Drives ZAP purely over its HTTP API — ZAP runs as a sidecar container.
Pipeline per scan: spider → (optional) ajax spider → active scan → alerts.
NOTE: one ZAP instance holds ONE session — serialize or use per-scan instances
(see TECH-NOTES pitfall #1).
"""

from __future__ import annotations

import asyncio
from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field
from typing import Any

import httpx

from app.core.config import settings


@dataclass
class ZapAlert:
    """Normalized ZAP alert — orchestrator converts this to a Finding row."""

    plugin_id: str  # ZAP rule id, e.g. "40018" (SQL Injection)
    name: str
    risk: str  # ZAP risk: informational | low | medium | high
    url: str
    param: str | None = None
    method: str | None = None
    cweid: str | None = None
    description: str | None = None
    evidence: str | None = None
    raw: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_api(cls, payload: dict[str, Any]) -> ZapAlert:
        return cls(
            plugin_id=str(payload.get("pluginId", "")),
            name=payload.get("name", "unnamed"),
            risk=payload.get("risk", "informational"),
            url=payload.get("url", ""),
            param=payload.get("param") or None,
            method=payload.get("method") or None,
            cweid=f"CWE-{payload['cweid']}" if payload.get("cweid") else None,
            description=payload.get("description"),
            evidence=payload.get("evidence"),
            raw=payload,
        )


class ZapError(RuntimeError):
    pass


class ZapClient:
    """Thin async wrapper over ZAP's /JSON API."""

    def __init__(self, base_url: str | None = None, api_key: str | None = None) -> None:
        self._base = (base_url or settings.ZAP_BASE_URL).rstrip("/")
        self._key = api_key or settings.ZAP_API_KEY

    def _client(self) -> httpx.AsyncClient:
        return httpx.AsyncClient(
            base_url=self._base,
            params={"apikey": self._key},
            timeout=httpx.Timeout(60.0, connect=10.0),
        )

    async def _get(self, path: str, **params: Any) -> dict[str, Any]:
        async with self._client() as client:
            resp = await client.get(f"/JSON/{path}", params=params)
        if resp.status_code >= 400:
            raise ZapError(f"ZAP {path} failed: HTTP {resp.status_code}: {resp.text[:200]}")
        data = resp.json()
        # ZAP wraps errors as {"code": "...", "message": "..."} with HTTP 200
        if "code" in data and "Response" not in data:
            raise ZapError(f"ZAP {path} error: {data.get('message')}")
        return data

    # ── Health ──────────────────────────────────────────────────────────

    async def version(self) -> str:
        # canonical component/view/action paths (compact aliases removed in ZAP 2.17)
        data = await self._get("core/view/version")
        return str(data.get("version", "?"))

    # ── Spider ──────────────────────────────────────────────────────────

    async def start_spider(self, target_url: str, max_children: int = 10) -> int:
        data = await self._get("spider/action/scan", url=target_url, maxChildren=max_children)
        return int(data.get("scan", "-1"))

    async def spider_progress(self, scan_id: int) -> int:
        """0..100; raises ZapError if ZAP reports the spider stopped with error."""
        data = await self._get("spider/view/status", scanId=str(scan_id))
        return int(data.get("status", "0"))

    async def spider_results(self, scan_id: int) -> list[str]:
        """URLs discovered by a finished spider run — feeds the XSS engine."""
        data = await self._get("spider/view/results", scanId=str(scan_id))
        return list(data.get("results", []))

    # ── Active scan ─────────────────────────────────────────────────────

    async def start_active_scan(self, target_url: str, policy: str | None = None) -> int:
        params: dict[str, Any] = {"url": target_url}
        if policy:
            params["scanPolicyName"] = policy
        data = await self._get("ascan/action/scan", **params)
        return int(data.get("scan", "-1"))

    async def active_scan_progress(self, scan_id: int) -> int:
        data = await self._get("ascan/view/status", scanId=str(scan_id))
        return int(data.get("status", "0"))

    # ── Results ─────────────────────────────────────────────────────────

    async def alerts(self, base_url: str) -> list[ZapAlert]:
        data = await self._get("core/view/alerts", baseurl=base_url)
        return [ZapAlert.from_api(item) for item in data.get("alerts", [])]

    async def stop_everything(self) -> None:
        """Best-effort cancel of all running scans — used on scan cancellation."""
        await self._get("ascan/action/stopAllScans")
        await self._get("spider/action/stopAllScans")

    async def stop_spider(self, scan_id: int) -> None:
        await self._get("spider/action/stop", scanId=str(scan_id))

    async def stop_active_scan(self, scan_id: int) -> None:
        await self._get("ascan/action/stop", scanId=str(scan_id))

    # ── Composite pipeline ──────────────────────────────────────────────

    async def run_full_scan(
        self, target_url: str, poll_seconds: float = 5.0, timeout: float = 1800.0
    ) -> list[ZapAlert]:
        """Spider → active scan → fetch alerts, with bounded polling."""
        spider_id = await self.start_spider(target_url)
        await self._poll_until_done(lambda: self.spider_progress(spider_id), poll_seconds, timeout)
        ascan_id = await self.start_active_scan(target_url)
        await self._poll_until_done(
            lambda: self.active_scan_progress(ascan_id), poll_seconds, timeout
        )
        return await self.alerts(target_url)

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
        raise ZapError(f"ZAP phase exceeded timeout of {timeout}s")  # ScanTimeout analog
