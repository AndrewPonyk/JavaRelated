"""Structured JSON logging with request-ID correlation.

Log contract (docs/ARCHITECTURE.md §2.6): every line carries
timestamp/level/event/request_id; handlers write to stdout (CloudWatch picks
it up in AWS). The request ID is stored in a contextvar so services/log calls
deep in the stack correlate for free.
"""

from __future__ import annotations

import json
import logging
import sys
from contextvars import ContextVar
from datetime import UTC, datetime

request_id_var: ContextVar[str] = ContextVar("request_id", default="-")


class _JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, object] = {
            "timestamp": datetime.now(UTC).isoformat(timespec="milliseconds"),
            "level": record.levelname,
            "event": record.getMessage(),
            "logger": record.name,
            "request_id": request_id_var.get(),
        }
        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)
        extra = getattr(record, "extra_fields", None)
        if isinstance(extra, dict):
            payload.update(extra)
        return json.dumps(payload, default=str)


def configure_logging(*, level: str = "INFO", debug: bool = False) -> None:
    """Idempotent root-logger setup. Human-readable in debug, JSON otherwise."""
    root = logging.getLogger()
    root.setLevel(level.upper())
    root.handlers.clear()

    handler = logging.StreamHandler(sys.stdout)
    if debug:
        handler.setFormatter(
            logging.Formatter("%(asctime)s %(levelname)-7s %(name)s — %(message)s")
        )
    else:
        handler.setFormatter(_JsonFormatter())
    root.addHandler(handler)

    logging.getLogger("uvicorn.access").setLevel(logging.WARNING)

    # Phase 3 roadmap (docs/PROJECT-PLAN.md): structlog processors and Sentry
    # shipping (release + request_id tags) replace this stdlib formatter.
