"""Scan-finished webhook notifications (Slack-compatible JSON).

Fire-and-forget: delivery failures are logged and counted, never raised —
a webhook outage must not affect scan results.
"""

from __future__ import annotations

import logging

import httpx

from app.core.config import settings
from app.core.metrics import metrics

logger = logging.getLogger(__name__)


async def send_scan_webhook(payload: dict) -> bool:
    """POST a JSON event; returns True on 2xx. No-op when unconfigured."""
    url = settings.SCAN_WEBHOOK_URL
    if not url:
        return False
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.post(
                url,
                json={
                    "text": (
                        f"🛡️ Scan {payload.get('scan_id')} of {payload.get('target_url')} "
                        f"{payload.get('status')}: "
                        + ", ".join(
                            f"{count} {sev}"
                            for sev, count in (payload.get("severity_counts") or {}).items()
                            if count
                        )
                    ),
                    **payload,
                },
            )
        ok = 200 <= resp.status_code < 300
        metrics.count(
            "webhook_notifications_total", labels={"outcome": "ok" if ok else "http_error"}
        )
        return ok
    except httpx.HTTPError as exc:
        logger.warning("scan webhook delivery failed: %s", exc)
        metrics.count("webhook_notifications_total", labels={"outcome": "network_error"})
        return False
