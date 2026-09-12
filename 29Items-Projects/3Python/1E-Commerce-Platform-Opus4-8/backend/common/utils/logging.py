"""Minimal structured JSON log formatter (12-factor: log to stdout)."""

from __future__ import annotations

import json
import logging

# Standard LogRecord attributes we don't want to duplicate in `extra`.
_RESERVED = set(logging.makeLogRecord({}).__dict__) | {"message", "asctime"}


class JSONFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload = {
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "time": self.formatTime(record, self.datefmt),
        }
        # Merge any structured `extra={...}` fields (e.g. request_id).
        for key, value in record.__dict__.items():
            if key not in _RESERVED and not key.startswith("_"):
                payload[key] = value
        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)
        return json.dumps(payload, default=str)
