"""Notification channels: structured log (always on), Slack webhooks, PagerDuty Events v2.

Delivery philosophy (ARCHITECTURE §2.6): notifiers are best-effort — the la-alerts index
is the source of truth, so a dead webhook never loses an alert, only its ping.
All senders are async (the engine runs on one event loop) and accept an httpx transport
so tests exercise the real request-building code without a network.
"""

from __future__ import annotations

import asyncio
import logging
from typing import Protocol

import httpx

from log_analytics.common.config import Settings
from log_analytics.common.models import AlertEvent, AlertSeverity

logger = logging.getLogger(__name__)

PAGERDUTY_EVENTS_URL = "https://events.pagerduty.com/v2/enqueue"

_SEVERITY_EMOJI = {
    AlertSeverity.INFO: "i",
    AlertSeverity.WARNING: "⚠️",
    AlertSeverity.CRITICAL: "🚨",
}

_RETRY_DELAYS = (0.0, 0.5)  # one immediate try + one short retry; alerting must stay fast


class Notifier(Protocol):
    name: str

    async def send(self, alert: AlertEvent) -> bool:
        """Return True on successful delivery. Never raise — log and return False."""
        ...


class LogNotifier:
    """Default channel: structured log line (dev, and the always-on fallback)."""

    name = "log"

    async def send(self, alert: AlertEvent) -> bool:
        logger.warning(
            "ALERT %s",
            alert.title,
            extra={
                "rule_id": alert.rule_id,
                "severity": alert.severity.value,
                "service": alert.service,
                "dedup_key": alert.dedup_key,
            },
        )
        return True


class _WebhookNotifier:
    """Shared retrying POST for HTTP-based channels."""

    def __init__(self, timeout: float, transport: httpx.AsyncBaseTransport | None) -> None:
        self._timeout = timeout
        self._transport = transport

    async def _post(self, url: str, payload: dict, ok_statuses: tuple[int, ...]) -> bool:
        for delay in _RETRY_DELAYS:
            if delay:
                await asyncio.sleep(delay)
            try:
                async with httpx.AsyncClient(
                    timeout=self._timeout, transport=self._transport
                ) as client:
                    resp = await client.post(url, json=payload)
                if resp.status_code in ok_statuses:
                    return True
                logger.error(
                    "%s webhook returned %s: %s",
                    type(self).__name__,
                    resp.status_code,
                    resp.text[:200],
                )
                if 400 <= resp.status_code < 500:
                    return False  # our payload is wrong — retrying won't help
            except httpx.HTTPError as exc:
                logger.error("%s delivery failed: %s", type(self).__name__, exc)
        return False


class SlackWebhookNotifier(_WebhookNotifier):
    name = "slack"

    def __init__(
        self,
        webhook_url: str,
        timeout: float = 5.0,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        super().__init__(timeout, transport)
        self._webhook_url = webhook_url

    async def send(self, alert: AlertEvent) -> bool:
        emoji = _SEVERITY_EMOJI.get(alert.severity, "⚠️")
        payload = {
            "text": f"{emoji} *{alert.title}*",
            "blocks": [
                {
                    "type": "section",
                    "text": {
                        "type": "mrkdwn",
                        "text": (
                            f"{emoji} *{alert.title}*\n"
                            f"severity: `{alert.severity.value}`"
                            f" · service: `{alert.service or '-'}`"
                            f" · source: `{alert.source}`\n{alert.description}"
                        ),
                    },
                }
            ],
        }
        return await self._post(self._webhook_url, payload, ok_statuses=(200,))


class PagerDutyNotifier(_WebhookNotifier):
    """PagerDuty Events API v2. dedup_key is passed through — PD dedups server-side too."""

    name = "pagerduty"

    def __init__(
        self,
        routing_key: str,
        timeout: float = 5.0,
        transport: httpx.AsyncBaseTransport | None = None,
        events_url: str = PAGERDUTY_EVENTS_URL,
    ) -> None:
        super().__init__(timeout, transport)
        self._routing_key = routing_key
        self._events_url = events_url

    @staticmethod
    def _pd_severity(severity: AlertSeverity) -> str:
        return {"info": "info", "warning": "warning", "critical": "critical"}[severity.value]

    def build_event(self, alert: AlertEvent) -> dict:
        return {
            "routing_key": self._routing_key,
            "event_action": "trigger",
            "dedup_key": alert.dedup_key or alert.id,
            "payload": {
                "summary": alert.title[:1024],
                "source": alert.service or "log-analytics-platform",
                "severity": self._pd_severity(alert.severity),
                "timestamp": alert.created_at.isoformat(),
                "group": alert.source,
                "custom_details": {"description": alert.description, **alert.context},
            },
        }

    async def send(self, alert: AlertEvent) -> bool:
        return await self._post(self._events_url, self.build_event(alert), ok_statuses=(202,))


def build_notifiers(
    settings: Settings, transport: httpx.AsyncBaseTransport | None = None
) -> dict[str, Notifier]:
    """Instantiate the channels that are actually configured; 'log' always exists."""
    notifiers: dict[str, Notifier] = {"log": LogNotifier()}
    if settings.slack_webhook_url:
        notifiers["slack"] = SlackWebhookNotifier(settings.slack_webhook_url, transport=transport)
    if settings.pagerduty_routing_key:
        notifiers["pagerduty"] = PagerDutyNotifier(
            settings.pagerduty_routing_key, transport=transport
        )
    return notifiers
