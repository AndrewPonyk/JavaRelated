"""Structured JSON logging for every service in the platform.

The platform eats its own dog food: services emit exactly the shape the pipeline ingests
(timestamp / level / service / message / attributes), so shipping our own logs through the
gateway is a config change, not a code change.
"""

from __future__ import annotations

import json
import logging
import sys
from datetime import datetime, timezone
from typing import Any

# Fields of LogRecord that we never copy into the structured payload.
_RESERVED = frozenset(
    {
        "args",
        "asctime",
        "created",
        "exc_info",
        "exc_text",
        "filename",
        "funcName",
        "levelname",
        "levelno",
        "lineno",
        "module",
        "msecs",
        "msg",
        "message",
        "name",
        "pathname",
        "process",
        "processName",
        "relativeCreated",
        "stack_info",
        "taskName",
        "thread",
        "threadName",
    }
)


class JsonFormatter(logging.Formatter):
    """Render records as single-line JSON conforming to the platform's LogEvent shape."""

    def __init__(self, service: str) -> None:
        super().__init__()
        self._service = service

    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, Any] = {
            "timestamp": datetime.fromtimestamp(record.created, tz=timezone.utc).isoformat(),
            "level": record.levelname,
            "service": self._service,
            "logger": record.name,
            "message": record.getMessage(),
        }
        # Anything passed via `extra=` becomes a structured attribute.
        attributes = {k: v for k, v in record.__dict__.items() if k not in _RESERVED}
        if attributes:
            payload["attributes"] = attributes
        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)
        return json.dumps(payload, default=str)


def configure_logging(service: str, level: str = "INFO") -> logging.Logger:
    """Configure root logging to stdout as JSON; return the service logger.

    Idempotent: safe to call from every entrypoint (uvicorn workers, Spark drivers, CLIs).
    """
    root = logging.getLogger()
    root.setLevel(level.upper())
    # Replace pre-existing handlers rather than stacking duplicates.
    for handler in list(root.handlers):
        root.removeHandler(handler)
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonFormatter(service=service))
    root.addHandler(handler)

    # Tame chatty third-party loggers; our own logs stay at the requested level.
    for noisy in ("uvicorn.access", "aiokafka", "urllib3", "httpx"):
        logging.getLogger(noisy).setLevel("WARNING")

    return logging.getLogger(service)
