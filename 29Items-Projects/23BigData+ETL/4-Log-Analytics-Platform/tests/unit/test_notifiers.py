"""Notifier channels: request building, status handling, retry semantics — no network."""

from __future__ import annotations

import asyncio
import json

import httpx

from log_analytics.alerting.notifiers import (
    LogNotifier,
    PagerDutyNotifier,
    SlackWebhookNotifier,
    build_notifiers,
)
from log_analytics.common.config import Settings
from log_analytics.common.models import AlertEvent, AlertSeverity

ALERT = AlertEvent(
    rule_id="high-error-ratio",
    severity=AlertSeverity.CRITICAL,
    title="[High error ratio] checkout",
    description="error_ratio 0.4 > 0.05",
    service="checkout",
    dedup_key="high-error-ratio|checkout",
    context={"observed": 0.4},
)


def _run(coro):
    return asyncio.run(coro)


def test_log_notifier_always_succeeds() -> None:
    assert _run(LogNotifier().send(ALERT)) is True


class TestSlack:
    def test_success_posts_blocks_payload(self) -> None:
        seen: list[httpx.Request] = []

        def handler(request: httpx.Request) -> httpx.Response:
            seen.append(request)
            return httpx.Response(200, text="ok")

        notifier = SlackWebhookNotifier(
            "https://hooks.slack.example/T/B/x", transport=httpx.MockTransport(handler)
        )
        assert _run(notifier.send(ALERT)) is True
        body = json.loads(seen[0].content)
        assert "checkout" in body["text"]
        assert body["blocks"][0]["text"]["text"].count("critical") == 1

    def test_server_errors_retry_then_fail(self) -> None:
        calls = {"n": 0}

        def handler(request: httpx.Request) -> httpx.Response:
            calls["n"] += 1
            return httpx.Response(500, text="oops")

        notifier = SlackWebhookNotifier(
            "https://x.example/h", transport=httpx.MockTransport(handler)
        )
        assert _run(notifier.send(ALERT)) is False
        assert calls["n"] == 2  # initial + one retry

    def test_client_errors_do_not_retry(self) -> None:
        calls = {"n": 0}

        def handler(request: httpx.Request) -> httpx.Response:
            calls["n"] += 1
            return httpx.Response(404, text="no_service")

        notifier = SlackWebhookNotifier(
            "https://x.example/h", transport=httpx.MockTransport(handler)
        )
        assert _run(notifier.send(ALERT)) is False
        assert calls["n"] == 1

    def test_network_failure_returns_false(self) -> None:
        def handler(request: httpx.Request) -> httpx.Response:
            raise httpx.ConnectError("refused", request=request)

        notifier = SlackWebhookNotifier(
            "https://x.example/h", transport=httpx.MockTransport(handler)
        )
        assert _run(notifier.send(ALERT)) is False


class TestPagerDuty:
    def test_event_shape(self) -> None:
        event = PagerDutyNotifier("rk-123").build_event(ALERT)
        assert event["routing_key"] == "rk-123"
        assert event["event_action"] == "trigger"
        assert event["dedup_key"] == "high-error-ratio|checkout"
        assert event["payload"]["severity"] == "critical"
        assert event["payload"]["source"] == "checkout"
        assert event["payload"]["custom_details"]["observed"] == 0.4

    def test_202_is_success(self) -> None:
        def handler(request: httpx.Request) -> httpx.Response:
            assert request.url == httpx.URL("https://events.pagerduty.com/v2/enqueue")
            return httpx.Response(202, json={"status": "success"})

        notifier = PagerDutyNotifier("rk-123", transport=httpx.MockTransport(handler))
        assert _run(notifier.send(ALERT)) is True


def test_build_notifiers_only_configured_channels() -> None:
    bare = build_notifiers(Settings(_env_file=None))
    assert set(bare) == {"log"}
    full = build_notifiers(
        Settings(_env_file=None, slack_webhook_url="https://x", pagerduty_routing_key="rk")
    )
    assert set(full) == {"log", "slack", "pagerduty"}
