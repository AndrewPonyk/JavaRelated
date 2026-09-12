"""Structured JSON logging for all lakehouse jobs.

Every record carries the job context (job name, run_date, run_id) so a single
log line is enough to reproduce a failure with a backfill command.
CloudWatch/OpenSearch parse the JSON directly.
"""

from __future__ import annotations

import json
import logging
import sys
from datetime import UTC, datetime
from typing import Any


class _JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, Any] = {
            "ts": datetime.now(tz=UTC).isoformat(timespec="milliseconds"),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        payload.update(getattr(record, "context", {}))
        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)
        return json.dumps(payload, default=str)


class _ContextAdapter(logging.LoggerAdapter):
    """Binds stable job context (job, run_date, run_id) to every record."""

    def process(self, msg: str, kwargs: Any) -> tuple[str, Any]:
        extra = kwargs.setdefault("extra", {})
        extra["context"] = {**(self.extra or {}), **extra.get("context", {})}
        return msg, kwargs


def get_logger(name: str, **context: Any) -> logging.LoggerAdapter:
    """Return a JSON logger bound to job context.

    Example:
        log = get_logger("bronze_to_silver", run_date="2026-07-01")
        log.info("merged rows", extra={"context": {"rows": 1234}})
    """
    root = logging.getLogger()
    if not root.handlers:
        handler = logging.StreamHandler(sys.stdout)
        handler.setFormatter(_JsonFormatter())
        root.addHandler(handler)
        root.setLevel(logging.INFO)
    return _ContextAdapter(logging.getLogger(name), context)
