"""Hot-reload of tuned anomaly parameters published by the retrain loop.

The anomaly_model_retrain DAG publishes to a Redis hash:
    anomaly:model  {version: "<id>", params: '{"metric": {"z_threshold": ...}}'}
(cloud environments additionally archive the artifact to S3). The processor
polls the hash and applies overrides in place — no restart, no rebalance.
"""

from __future__ import annotations

import asyncio
import contextlib
import json
import logging

import redis.asyncio as aioredis

from processor.anomaly.detector import EwmaAnomalyDetector

log = logging.getLogger(__name__)


class ModelReloader:
    def __init__(
        self,
        url: str,
        detector: EwmaAnomalyDetector,
        *,
        key: str = "anomaly:model",
        poll_seconds: int = 60,
        client: aioredis.Redis | None = None,
    ) -> None:
        self._url = url
        self._detector = detector
        self._key = key
        self._poll_seconds = poll_seconds
        self._redis = client
        self.current_version: str | None = None

    def _client(self) -> aioredis.Redis:
        if self._redis is None:
            self._redis = aioredis.from_url(self._url, decode_responses=True)
        return self._redis

    async def check_once(self) -> bool:
        """Apply the published model if its version changed. Returns True on apply."""
        data = await self._client().hgetall(self._key)
        version = data.get("version")
        if not version or version == self.current_version:
            return False
        try:
            overrides = json.loads(data.get("params", "{}"))
        except json.JSONDecodeError:
            log.error("model %s has unparseable params; keeping current model", version)
            return False
        self._detector.apply_overrides(overrides)
        self.current_version = version
        log.info("anomaly model reloaded: version=%s metrics=%d", version, len(overrides))
        return True

    async def run(self, stop: asyncio.Event) -> None:
        while not stop.is_set():
            try:
                await self.check_once()
            except Exception:  # transient redis failures must not kill the poller
                log.exception("model reload check failed; retrying next cycle")
            with contextlib.suppress(TimeoutError):
                await asyncio.wait_for(stop.wait(), timeout=self._poll_seconds)
