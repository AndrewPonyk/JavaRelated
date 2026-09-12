"""OpenSearch bulk sink for Structured Streaming via foreachBatch.

Why REST-per-partition instead of the elasticsearch-hadoop/opensearch-hadoop jar:
no Scala dependency to version-match, retries under our control, and the same code path
works against local OpenSearch and AWS domains (add SigV4 at the session).
"""

from __future__ import annotations

import json
import logging
import os
import time
from collections.abc import Callable, Iterator
from datetime import datetime
from typing import Any

from pyspark.sql import DataFrame, Row

logger = logging.getLogger(__name__)

_BULK_CHUNK = 500
_MAX_RETRIES = 4


def _json_default(obj: Any) -> str:
    # datetime MUST be ISO-8601: `str(datetime)` uses a space separator, which the
    # strict date mapping rejects (silent-looking bulk `errors: true`).
    if isinstance(obj, datetime):
        return obj.isoformat()
    return str(obj)


def _index_partition(rows: Iterator[Row], *, index: str, id_column: str | None) -> None:
    """Runs on executors. Uses env config (executors don't see driver Settings objects)."""
    import httpx  # local import: executor-side dependency

    base_url = os.getenv("LA_OPENSEARCH_URL", "http://localhost:9200").rstrip("/")
    username = os.getenv("LA_OPENSEARCH_USERNAME", "")
    auth = (username, os.getenv("LA_OPENSEARCH_PASSWORD", "")) if username else None
    # TODO(prod): SigV4 request signing for AWS OpenSearch (requests-aws4auth / opensearch-py).

    def flush(lines: list[str]) -> None:
        if not lines:
            return
        payload = "\n".join(lines) + "\n"
        last_detail = ""
        for attempt in range(_MAX_RETRIES):
            with httpx.Client(timeout=30.0, auth=auth) as client:
                resp = client.post(
                    f"{base_url}/_bulk",
                    content=payload,
                    headers={"Content-Type": "application/x-ndjson"},
                )
            if resp.status_code == 200:
                body = resp.json()
                if not body.get("errors"):
                    return
                # Surface the cluster's first per-item complaint — mapping rejections
                # otherwise look like generic failures.
                failed = [
                    item["index"] for item in body.get("items", []) if "error" in item["index"]
                ]
                last_detail = f"{len(failed)} item(s) rejected; first: {failed[0]['error']}"
            else:
                last_detail = f"HTTP {resp.status_code}: {resp.text[:300]}"
            if resp.status_code in (200, 429, 502, 503):
                time.sleep(min(2**attempt, 10))  # backoff on pressure, then retry
                continue
            break
        # Crash the task → Spark retries the batch; idempotent _id absorbs duplicates.
        raise RuntimeError(
            f"bulk indexing to {index} failed after {_MAX_RETRIES} attempts — {last_detail}"
        )

    buffer: list[str] = []
    for row in rows:
        doc: dict[str, Any] = row.asDict(recursive=True)
        doc_id = doc.pop(id_column, None) if id_column else None
        # `timestamp` column → @timestamp field expected by the index templates.
        if "timestamp" in doc:
            doc["@timestamp"] = doc.pop("timestamp")
        action: dict[str, Any] = {"index": {"_index": index}}
        if doc_id:
            action["index"]["_id"] = doc_id
        buffer.append(json.dumps(action, default=_json_default))
        buffer.append(json.dumps(doc, default=_json_default))
        if len(buffer) >= _BULK_CHUNK * 2:
            flush(buffer)
            buffer = []
    flush(buffer)


def foreach_batch_indexer(
    index: str, id_column: str | None = "doc_id"
) -> Callable[[DataFrame, int], None]:
    """Build a foreachBatch function bulk-indexing every micro-batch into `index` (write alias)."""

    def _write(batch_df: DataFrame, batch_id: int) -> None:
        count = batch_df.count()
        if count == 0:
            return
        logger.info("indexing batch %s (%s docs) into %s", batch_id, count, index)
        batch_df.foreachPartition(
            lambda rows: _index_partition(rows, index=index, id_column=id_column)
        )

    return _write
