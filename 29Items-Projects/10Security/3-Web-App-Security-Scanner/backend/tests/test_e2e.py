"""E2E: real backend + real ZAP sidecar + real XSS engine against the
vuln-lab target. Run via `make test-e2e` (boots the compose stack with the
e2e profile). Skipped unless WSS_E2E=1, so the normal suite and CI never
require running sidecars.
"""

from __future__ import annotations

import asyncio
import os
import time

import httpx
import pytest

pytestmark = [
    pytest.mark.e2e,
    pytest.mark.skipif(
        os.environ.get("WSS_E2E") != "1",
        reason="e2e requires the compose stack with the e2e profile: make test-e2e",
    ),
]

API = os.environ.get("WSS_E2E_API", "http://localhost:8000/api/v1")
ADMIN_EMAIL = os.environ.get("WSS_E2E_ADMIN_EMAIL", "admin@scanner.local")
ADMIN_PASSWORD = os.environ.get("WSS_E2E_ADMIN_PASSWORD", "change-me-admin")
TARGET = os.environ.get("WSS_E2E_TARGET", "http://vuln-lab:8080")
POLL_SECONDS = 5
MAX_WAIT_SECONDS = 600  # ZAP active scan dominates; generous CI ceiling


async def _get(client: httpx.AsyncClient, url: str, headers: dict) -> httpx.Response:
    """GET with one transport-level retry — long e2e polls cross a Docker
    port-forward whose keepalive connections can be dropped mid-poll."""
    try:
        return await client.get(url, headers=headers)
    except httpx.TransportError:
        await asyncio.sleep(2)
        return await client.get(url, headers=headers, timeout=30)


async def _run_scan(client: httpx.AsyncClient, headers: dict) -> tuple[int, dict]:
    """Launch a fast-profile scan and poll it to a terminal state."""
    created = await client.post(
        f"{API}/scans", headers=headers, json={"target_url": TARGET, "profile": "fast"}
    )
    assert created.status_code == 201, created.text
    scan_id = created.json()["id"]

    deadline = time.monotonic() + MAX_WAIT_SECONDS
    while time.monotonic() < deadline:
        progress = (await _get(client, f"{API}/scans/{scan_id}/progress", headers)).json()
        if progress["phase"] in ("done", "failed"):
            break
        await asyncio.sleep(POLL_SECONDS)
    else:
        pytest.fail(f"scan {scan_id} did not reach a terminal state within {MAX_WAIT_SECONDS}s")

    scan = (await _get(client, f"{API}/scans/{scan_id}", headers)).json()
    return scan_id, scan


async def test_scan_pipeline_produces_findings_and_reports():
    # No keepalive: every request gets a fresh connection, so a NAT/port-forward
    # dropping idle connections between polls can't poison the run.
    async with httpx.AsyncClient(
        timeout=30, limits=httpx.Limits(max_keepalive_connections=0)
    ) as client:
        login = await client.post(
            f"{API}/auth/login", json={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD}
        )
        assert login.status_code == 200, login.text
        headers = {"Authorization": f"Bearer {login.json()['access_token']}"}

        scan_id, scan = await _run_scan(client, headers)
        if scan["status"] == "failed" and "zap" in str(scan.get("error_detail", "")):
            # ZAP daemon can still be booting right after `compose up` — one retry
            await asyncio.sleep(15)
            scan_id, scan = await _run_scan(client, headers)
        assert scan["status"] == "completed", scan.get("error_detail")

        findings = (
            await client.get(
                f"{API}/findings",
                headers=headers,
                params={"scan_id": scan_id, "page_size": 100},
            )
        ).json()
        assert findings["total"] >= 1, "vuln-lab must yield at least one finding"
        sources = {f["source"] for f in findings["items"]}
        assert "xss_engine" in sources, f"expected XSS hits on ?q=, got sources={sources}"
        assert all(
            f["severity"] in ("info", "low", "medium", "high", "critical")
            for f in findings["items"]
        )

        sarif = await client.get(f"{API}/reports/{scan_id}/sarif", headers=headers)
        assert sarif.status_code == 200
        assert sarif.json()["version"] == "2.1.0"
