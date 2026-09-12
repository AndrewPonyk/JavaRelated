"""Structured logging utilities: JSON formatting + PHI scrubbing.

Referenced from ``settings.LOGGING``. The scrubber is a defense-in-depth layer:
PHI should never be passed to the logger in the first place, but if it slips
through, it must not be persisted in clear text (HIPAA).
"""
from __future__ import annotations

import json
import logging
import re
from datetime import UTC, datetime

# Coarse identifier patterns. TODO: extend with MRN/DOB-shaped patterns and
# integrate a clinical de-id library for free-text fields.
_PHI_PATTERNS: list[tuple[re.Pattern[str], str]] = [
    (re.compile(r"\b\d{3}-\d{2}-\d{4}\b"), "[SSN]"),
    (re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+"), "[EMAIL]"),
    (re.compile(r"\b\d{10,}\b"), "[NUM]"),
]


class PHIScrubFilter(logging.Filter):
    """Masks likely PHI in the log message before it is emitted."""

    def filter(self, record: logging.LogRecord) -> bool:
        if isinstance(record.msg, str):
            record.msg = self._scrub(record.msg)
        return True

    @staticmethod
    def _scrub(text: str) -> str:
        for pattern, repl in _PHI_PATTERNS:
            text = pattern.sub(repl, text)
        return text


class JsonFormatter(logging.Formatter):
    """Minimal structured JSON formatter with a correlation id slot."""

    def format(self, record: logging.LogRecord) -> str:
        payload = {
            "ts": datetime.now(UTC).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "msg": record.getMessage(),
            "correlation_id": getattr(record, "correlation_id", None),
        }
        if record.exc_info:
            payload["exc"] = self.formatException(record.exc_info)
        return json.dumps(payload, default=str)
