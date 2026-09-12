"""Warehouse sink — LOCAL DEV ONLY, disabled by default.

In cloud environments the processor does NOT write to Snowflake. Warehouse
ingestion of the event stream is a separate branch off Kafka:

    MSK → MSK Connect (Snowflake Kafka connector, Snowpipe Streaming)
        → RAW.EVENTS.STREAM_EVENTS

(exactly-once via connector offsets; see docs/ARCHITECTURE.md §2.2). Keeping
ingest out of this process protects the sub-second hot path from warehouse
latency and credential scope.

For local parity testing against a dev Snowflake account, enable with
DEV_SNOWFLAKE_SINK_ENABLED=true and provide SNOWFLAKE_* env vars — the sink
then micro-batches INSERTs into the same landing table the connector targets.
Requires `pip install snowflake-connector-python` (intentionally not in the
default image).
"""

from __future__ import annotations

import asyncio
import json
import logging
import os

from processor.metrics_aggregator import Event

log = logging.getLogger(__name__)

_INSERT_SQL = (
    "insert into raw.events.stream_events (record_content) "
    "select parse_json(column1) from values (%s)"
)


class SnowflakeDevSink:
    def __init__(self, flush_every: int = 500) -> None:
        self._buffer: list[Event] = []
        self._flush_every = flush_every
        self.rows_written = 0

    async def add(self, event: Event) -> None:
        self._buffer.append(event)
        if len(self._buffer) >= self._flush_every:
            await self.flush()

    async def flush(self) -> None:
        if not self._buffer:
            return
        batch, self._buffer = self._buffer, []
        await asyncio.to_thread(self._flush_sync, batch)

    def _flush_sync(self, batch: list[Event]) -> None:
        import snowflake.connector  # lazy: optional dev dependency

        connection = snowflake.connector.connect(
            account=os.environ["SNOWFLAKE_ACCOUNT"],
            user=os.environ["SNOWFLAKE_USER"],
            password=os.environ["SNOWFLAKE_PASSWORD"],
            role=os.environ.get("SNOWFLAKE_ROLE", "LOADER"),
            warehouse=os.environ.get("SNOWFLAKE_WAREHOUSE", "LOAD_WH"),
            database="RAW",
            session_parameters={"QUERY_TAG": "dev-stream-sink"},
        )
        try:
            payloads = [
                (
                    json.dumps(
                        {
                            "event_id": e.event_id,
                            "event_type": e.event_type,
                            "ts_ms": e.ts_ms,
                            "amount": e.amount,
                            **e.attrs,
                        }
                    ),
                )
                for e in batch
            ]
            with connection.cursor() as cursor:
                cursor.executemany(_INSERT_SQL, payloads)
            connection.commit()
            self.rows_written += len(batch)
            log.info("dev sink: wrote %d events to RAW.EVENTS.STREAM_EVENTS", len(batch))
        finally:
            connection.close()
