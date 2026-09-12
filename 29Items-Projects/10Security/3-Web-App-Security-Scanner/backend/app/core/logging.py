"""Structured JSON logging with secret redaction.

Every log line is one JSON object so staging/prod can ship lines to any
aggregator without regex parsing. A redaction processor strips credentials
before they reach stdout — evidence payloads frequently contain tokens.
"""

from __future__ import annotations

import json
import logging
import re
import sys

_REDACT_PATTERNS = [
    re.compile(r"(?i)(authorization\s*[:=]\s*)(\S+)"),
    re.compile(r"(?i)(set-cookie\s*:\s*)(\S+)"),
    re.compile(r"(?i)(api[_-]?key['\"]?\s*[:=]\s*)(\S+)"),
    re.compile(r"(?i)(password['\"]?\s*[:=]\s*)(\S+)"),
]
_PLACEHOLDER = r"\1[REDACTED]"


def redact(message: str) -> str:
    for pattern in _REDACT_PATTERNS:
        message = pattern.sub(_PLACEHOLDER, message)
    return message


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload = {
            "ts": self.formatTime(record, "%Y-%m-%dT%H:%M:%S%z"),
            "level": record.levelname,
            "logger": record.name,
            "message": redact(record.getMessage()),
        }
        # Correlation ids — set by middleware / orchestrator via logging contexts
        for key in ("request_id", "scan_id"):
            if (value := getattr(record, key, None)) is not None:
                payload[key] = value
        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)
        return json.dumps(payload)


def setup_logging(level: int | None = None) -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonFormatter())
    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(level or (logging.DEBUG if settings_env_is_dev() else logging.INFO))


def settings_env_is_dev() -> bool:
    from app.core.config import settings

    return settings.ENV == "development"
