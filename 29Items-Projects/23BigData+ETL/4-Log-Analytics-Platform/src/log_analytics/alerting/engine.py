"""Alerting engine: consumes alerts.events + logs.anomalies, dedups, routes, records.

Run: python -m log_analytics.alerting.engine

Responsibilities:
  1. anomalies → alerts: `anomaly_score` rules from config/alert_rules.yaml decide the
     threshold/severity/channels (the highest-threshold matching rule wins); if no such
     rules are configured, the LA_ANOMALY_ALERT_THRESHOLD/_CRITICAL_ baselines apply.
  2. pattern alerts (from the Spark job) → channels resolved from the rule definition.
  3. dedup: Redis SETNX with TTL (in-memory fallback keeps a dev box working without Redis).
  4. dispatch to channels; a failed channel falls back to the log channel.
  5. every alert is indexed into la-alerts — the audit trail behind the triage dashboard.
  6. "service went silent" detection: periodic OpenSearch aggregation comparing services
     active in the last 24 h vs the last N minutes (windowed Spark metrics cannot see the
     absence of data; this can).
  7. offsets are committed only after a message is fully handled.
"""

from __future__ import annotations

import asyncio
import json
import logging
import time
from typing import Any

import httpx

from log_analytics.alerting.notifiers import Notifier, build_notifiers
from log_analytics.alerting.rules import ThresholdRule, evaluate, load_rules
from log_analytics.common.config import Settings, get_settings
from log_analytics.common.kafka import TOPIC_ALERTS, TOPIC_LOGS_ANOMALIES, make_consumer
from log_analytics.common.logging import configure_logging
from log_analytics.common.models import AlertEvent, AlertSeverity, AnomalyRecord
from log_analytics.common.opensearch import OpenSearchError, async_client, request_json
from log_analytics.ml.features import FEATURE_COLUMNS

logger = logging.getLogger(__name__)

CONSUMER_GROUP = "alerting-engine"
ALERTS_INDEX = "la-alerts"
LOGS_ALIAS = "la-logs"
DEFAULT_CHANNELS: tuple[str, ...] = ("log", "slack")


class Deduplicator:
    """SETNX-with-TTL semantics; in-process fallback when Redis is absent/unreachable."""

    def __init__(self, redis_url: str | None, ttl_seconds: int) -> None:
        self._ttl = ttl_seconds
        self._local: dict[str, float] = {}
        self._redis = None
        if redis_url:
            try:
                import redis

                self._redis = redis.Redis.from_url(redis_url, socket_connect_timeout=2)
                self._redis.ping()
            except Exception:  # degrade to local dedup, keep alerting alive
                logger.warning("redis unavailable — using in-memory dedup (per-process only)")
                self._redis = None

    def should_fire(self, dedup_key: str) -> bool:
        """True exactly once per key per TTL window."""
        if self._redis is not None:
            return bool(self._redis.set(f"la:alert:{dedup_key}", "1", nx=True, ex=self._ttl))
        now = time.monotonic()
        if self._local.get(dedup_key, 0.0) > now:
            return False
        self._local[dedup_key] = now + self._ttl
        return True


def find_silent_services(active: set[str], recent: set[str]) -> set[str]:
    """Services with traffic in the long window but none in the short one."""
    return active - recent


class AlertingEngine:
    def __init__(
        self,
        settings: Settings,
        notifiers: dict[str, Notifier] | None = None,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._settings = settings
        self._notifiers = notifiers if notifiers is not None else build_notifiers(settings)
        self._dedup = Deduplicator(settings.redis_url or None, settings.alert_dedup_ttl_seconds)
        self._os = async_client(
            settings.opensearch_url, auth=settings.opensearch_auth, transport=transport
        )
        try:
            self._rules: list[ThresholdRule] = load_rules(settings.rules_path)
        except FileNotFoundError:
            logger.warning("rules file %s not found — using built-in defaults", settings.rules_path)
            self._rules = []
        self._rules_by_id = {r.id: r for r in self._rules}
        self._anomaly_rules = sorted(
            (r for r in self._rules if r.metric == "anomaly_score" and r.enabled),
            key=lambda r: r.threshold,
            reverse=True,
        )

    # ── anomaly → alert ───────────────────────────────────────────────────

    def anomaly_to_alert(self, record: AnomalyRecord) -> tuple[AlertEvent, tuple[str, ...]] | None:
        """Map a scored window to (alert, channels), or None below every threshold.

        With anomaly_score rules configured, the highest-threshold matching rule wins
        (one alert per anomaly, most severe interpretation). Without rules, the settings
        baselines apply.
        """
        rule_id, severity, channels, threshold = None, None, DEFAULT_CHANNELS, None
        if self._anomaly_rules:
            matched = next(
                (r for r in self._anomaly_rules if evaluate(r, {"anomaly_score": record.score})),
                None,
            )
            if matched is None:
                return None
            rule_id, severity, channels, threshold = (
                matched.id,
                matched.severity,
                tuple(matched.channels),
                matched.threshold,
            )
        else:
            if record.score < self._settings.anomaly_alert_threshold:
                return None
            critical = record.score >= self._settings.anomaly_critical_threshold
            rule_id = "ml-anomaly"
            severity = AlertSeverity.CRITICAL if critical else AlertSeverity.WARNING
            threshold = self._settings.anomaly_alert_threshold

        alert = AlertEvent(
            rule_id=rule_id,
            severity=severity,
            title=f"[ML anomaly] {record.service} (score {record.score:.2f})",
            description=(
                f"Isolation Forest flagged {record.service} for window "
                f"{record.window_start:%H:%M}-{record.window_end:%H:%M} UTC "
                f"(threshold {threshold})."
            ),
            source="anomaly",
            service=record.service,
            dedup_key=f"{rule_id}|{record.service}",
            context={
                "score": record.score,
                "features": record.features,
                "model_version": record.model_version,
            },
        )
        return alert, channels

    # ── message handling ──────────────────────────────────────────────────

    @staticmethod
    def parse_anomaly(payload: dict[str, Any]) -> AnomalyRecord:
        """The scoring job emits feature columns flat; fold them into `features`."""
        features = {c: float(payload[c]) for c in FEATURE_COLUMNS if payload.get(c) is not None}
        return AnomalyRecord.model_validate({**payload, "features": features})

    def parse_pattern_alert(self, payload: dict[str, Any]) -> tuple[AlertEvent, tuple[str, ...]]:
        """Pattern-job rows carry `context` as a JSON string and no channels — resolve both."""
        if isinstance(payload.get("context"), str):
            try:
                payload = {**payload, "context": json.loads(payload["context"])}
            except json.JSONDecodeError:
                payload = {**payload, "context": {"raw": payload["context"]}}
        alert = AlertEvent.model_validate(payload)
        rule = self._rules_by_id.get(alert.rule_id)
        channels = tuple(rule.channels) if rule else DEFAULT_CHANNELS
        return alert, channels

    async def handle(self, topic: str, payload: dict[str, Any]) -> None:
        if topic == TOPIC_LOGS_ANOMALIES:
            result = self.anomaly_to_alert(self.parse_anomaly(payload))
            if result is None:
                return
            alert, channels = result
        else:
            alert, channels = self.parse_pattern_alert(payload)

        if not self._dedup.should_fire(alert.dedup_key):
            logger.debug("suppressed duplicate alert %s", alert.dedup_key)
            return
        await self.dispatch(alert, channels)

    async def dispatch(self, alert: AlertEvent, channels: tuple[str, ...]) -> None:
        """Send to every configured channel; guarantee the alert lands in the log exactly
        once whenever any channel failed (or none delivered) — never twice."""
        results: dict[str, bool] = {}
        for channel in channels:
            notifier = self._notifiers.get(channel)
            if notifier is None:
                continue
            results[channel] = await notifier.send(alert)
        needs_log_fallback = not any(results.values()) or not all(results.values())
        if needs_log_fallback and not results.get("log") and "log" in self._notifiers:
            await self._notifiers["log"].send(alert)
        await self._index_alert(alert)

    async def _index_alert(self, alert: AlertEvent) -> None:
        """la-alerts is the audit trail — failures are logged loudly, never fatal."""
        doc = alert.model_dump(mode="json")
        doc["@timestamp"] = doc.pop("created_at")
        for attempt in (1, 2):
            try:
                await request_json(self._os, "PUT", f"/{ALERTS_INDEX}/_doc/{alert.id}", json=doc)
                return
            except OpenSearchError as exc:
                if attempt == 2:
                    logger.error("alert indexing failed: %s", exc)
                else:
                    await asyncio.sleep(0.5)

    # ── silent-service detection ──────────────────────────────────────────

    async def _services_seen_since(self, minutes: int) -> set[str]:
        data = await request_json(
            self._os,
            "POST",
            f"/{LOGS_ALIAS}/_search",
            json={
                "size": 0,
                "query": {"range": {"@timestamp": {"gte": f"now-{minutes}m"}}},
                "aggs": {"services": {"terms": {"field": "service", "size": 1000}}},
            },
        )
        buckets = data.get("aggregations", {}).get("services", {}).get("buckets", [])
        return {b["key"] for b in buckets}

    async def check_silent_services(self) -> list[AlertEvent]:
        after = self._settings.silent_service_after_minutes
        active = await self._services_seen_since(24 * 60)
        recent = await self._services_seen_since(after)
        return [
            AlertEvent(
                rule_id="service-silent",
                severity=AlertSeverity.WARNING,
                title=f"[Service silent] {service}",
                description=(
                    f"{service} logged in the last 24h but nothing for {after}+ minutes — "
                    "crashed logger, broken agent, or a dead service."
                ),
                source="pattern",
                service=service,
                dedup_key=f"service-silent|{service}",
            )
            for service in sorted(find_silent_services(active, recent))
        ]

    async def _silent_loop(self) -> None:
        interval = self._settings.silent_service_check_seconds
        while True:
            await asyncio.sleep(interval)
            try:
                for alert in await self.check_silent_services():
                    if self._dedup.should_fire(alert.dedup_key):
                        await self.dispatch(alert, DEFAULT_CHANNELS)
            except OpenSearchError as exc:
                logger.warning("silent-service check skipped: %s", exc)
            except Exception:
                logger.exception("silent-service check failed")

    # ── run loop ──────────────────────────────────────────────────────────

    async def run(self) -> None:
        consumer = make_consumer(
            TOPIC_ALERTS,
            TOPIC_LOGS_ANOMALIES,
            bootstrap_servers=self._settings.kafka_bootstrap_servers,
            group_id=CONSUMER_GROUP,
            security_protocol=self._settings.kafka_security_protocol,
            sasl_mechanism=self._settings.kafka_sasl_mechanism,
            sasl_username=self._settings.kafka_sasl_username,
            sasl_password=self._settings.kafka_sasl_password,
        )
        await consumer.start()
        silent_task = (
            asyncio.create_task(self._silent_loop())
            if self._settings.silent_service_check_seconds > 0
            else None
        )
        logger.info(
            "alerting engine consuming %s, %s (%d rules loaded)",
            TOPIC_ALERTS,
            TOPIC_LOGS_ANOMALIES,
            len(self._rules),
        )
        try:
            async for message in consumer:
                try:
                    await self.handle(message.topic, message.value)
                except Exception:
                    # Poison alert payloads must not stall the stream; log + move on.
                    logger.exception(
                        "failed handling message",
                        extra={"topic": message.topic, "offset": message.offset},
                    )
                await consumer.commit()
        finally:
            if silent_task is not None:
                silent_task.cancel()
            await consumer.stop()
            await self._os.aclose()


def main() -> None:
    settings = get_settings()
    configure_logging(service="alerting-engine", level=settings.log_level)
    asyncio.run(AlertingEngine(settings).run())


if __name__ == "__main__":
    main()
