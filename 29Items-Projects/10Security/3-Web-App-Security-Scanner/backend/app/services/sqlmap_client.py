"""sqlmapapi REST client (async httpx).

Drives the SQLMap REST API: task lifecycle = new → options → start → poll
status → parse datadir log. CRITICAL: always destroy the task in finally —
zombie tasks leak RAM on the sqlmap sidecar (TECH-NOTES pitfall #2).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

import httpx

from app.core.config import settings

# Conservative defaults — we test for the flaw, not dump the database.
_SAFE_OPTIONS: dict[str, Any] = {
    "level": 1,
    "risk": 1,
    "threads": 3,
    "batch": True,  # never prompt interactively
    "getTables": False,  # no data exfiltration by default
    "dumpTable": False,
}


@dataclass
class SqlmapFinding:
    """Normalized SQLMap detection."""

    url: str
    parameter: str | None
    technique: str | None  # B=boolean-blind, T=time-blind, U=union, E=error
    dbms: str | None
    raw_log: dict[str, Any] = field(default_factory=dict)


class SqlmapError(RuntimeError):
    pass


class SqlmapClient:
    def __init__(self, base_url: str | None = None) -> None:
        self._base = (base_url or settings.SQLMAP_BASE_URL).rstrip("/")

    def _client(self) -> httpx.AsyncClient:
        return httpx.AsyncClient(
            base_url=self._base,
            auth=(settings.SQLMAP_USERNAME, settings.SQLMAP_PASSWORD),  # sqlmapapi Basic auth
            timeout=httpx.Timeout(30.0, connect=10.0),
        )

    async def _req(self, method: str, path: str, **kw: Any) -> Any:
        async with self._client() as client:
            resp = await client.request(method, path, **kw)
        if resp.status_code >= 400:
            raise SqlmapError(f"sqlmapapi {path} HTTP {resp.status_code}: {resp.text[:200]}")
        return resp.json()

    async def create_task(self) -> str:
        # NOTE: current sqlmapapi routes use GET for task lifecycle verbs
        data = await self._req("GET", "/task/new")
        if not data.get("success"):
            raise SqlmapError("task/new failed")
        return data["taskid"]

    async def set_options(self, task_id: str, target_url: str, **overrides: Any) -> None:
        options = {**_SAFE_OPTIONS, "url": target_url, **overrides}
        await self._req("POST", f"/option/{task_id}/set", json=options)

    async def start(self, task_id: str) -> None:
        await self._req("POST", f"/scan/{task_id}/start", json={})

    async def status(self, task_id: str) -> dict[str, Any]:
        data = await self._req("GET", f"/scan/{task_id}/status")
        return data  # {"status": "running|terminated|error", "returncode": ...}

    async def data(self, task_id: str) -> dict[str, Any]:
        """Full detection data — parse for injection points."""
        return await self._req("GET", f"/scan/{task_id}/data")

    async def destroy(self, task_id: str) -> None:
        await self._req("GET", f"/task/{task_id}/delete")

    async def run_scan(
        self, target_url: str, poll_seconds: float = 5.0, timeout: float = 900.0
    ) -> list[SqlmapFinding]:
        """Full task lifecycle with guaranteed cleanup; parses detections."""
        import asyncio
        import time

        task_id = await self.create_task()
        try:
            await self.set_options(task_id, target_url)
            await self.start(task_id)
            deadline = time.monotonic() + timeout
            while time.monotonic() < deadline:
                status = await self.status(task_id)
                if status.get("status") in {"terminated", "error"}:
                    break
                await asyncio.sleep(poll_seconds)
            else:
                raise SqlmapError(f"sqlmap task exceeded timeout of {timeout}s")
            return self._parse_data(await self.data(task_id))
        finally:
            await self.destroy(task_id)

    @staticmethod
    def _parse_data(data: dict[str, Any]) -> list[SqlmapFinding]:
        """sqlmap data shape: {"data": [ {"url": ..., "query": ..., "dbms": ...,
        "value": {PARAM: {"technique": ..., ...}}} ]} — tolerant parsing."""
        findings: list[SqlmapFinding] = []
        for entry in data.get("data", []):
            url = entry.get("url", "")
            dbms = entry.get("dbms")
            for param, detail in (entry.get("value") or {}).items():
                technique = detail.get("technique") if isinstance(detail, dict) else None
                findings.append(
                    SqlmapFinding(
                        url=url,
                        parameter=param,
                        technique=str(technique) if technique is not None else None,
                        dbms=dbms,
                        raw_log=detail if isinstance(detail, dict) else {},
                    )
                )
        return findings
