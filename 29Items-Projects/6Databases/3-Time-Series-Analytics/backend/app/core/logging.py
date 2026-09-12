"""Logging setup: human-readable in dev, JSON lines in staging/production.

Philosophy in docs/ARCHITECTURE.md §2.6 — logs are for forensics; trends and
alerting live in InfluxDB/Grafana.
"""

from __future__ import annotations

import json
import logging
from datetime import UTC, datetime


class JsonFormatter(logging.Formatter):
    """Minimal JSON-lines formatter (stdlib only, no extra dependency).

    Request/error correlation uses the X-Request-ID header echoed in error
    bodies; enriching every log line with it (logging.Filter bound to the
    request middleware) is a Phase 3 observability item in PROJECT-PLAN.md.
    """

    def format(self, record: logging.LogRecord) -> str:
        payload = {
            "ts": datetime.now(UTC).isoformat(timespec="milliseconds"),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        if record.exc_info:
            payload["exc"] = self.formatException(record.exc_info)
        return json.dumps(payload, ensure_ascii=False)


def configure_logging(level: str = "INFO", fmt: str = "plain") -> None:
    handler = logging.StreamHandler()
    if fmt == "json":
        handler.setFormatter(JsonFormatter())
    else:
        handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)-7s %(name)s: %(message)s"))

    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(level.upper())

    # Driver internals are chatty at INFO.
    logging.getLogger("cassandra").setLevel(logging.WARNING)
