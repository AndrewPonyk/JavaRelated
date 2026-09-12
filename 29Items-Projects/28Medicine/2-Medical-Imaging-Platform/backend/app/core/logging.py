"""Structured JSON logging with PHI redaction.

CRITICAL (HIPAA): patient identifiers must never reach logs. The redacting
processor scrubs known PHI keys before any record is emitted. Logs carry a
`correlation_id` so a single ingest is traceable API → queue → worker → ML.
"""

from __future__ import annotations

import logging
from typing import Any

import structlog

# DICOM/PHI keys that must be scrubbed if they ever appear in log event dicts.
_PHI_KEYS = frozenset(
    {
        "patient_name",
        "patientname",
        "patient_id",
        "patientid",
        "patient_birth_date",
        "patientbirthdate",
        "other_patient_ids",
        "ssn",
        "accession_number",
    }
)
_REDACTED = "***REDACTED-PHI***"


def _redact_phi(_: Any, __: str, event_dict: dict[str, Any]) -> dict[str, Any]:
    for key in list(event_dict):
        if key.lower() in _PHI_KEYS:
            event_dict[key] = _REDACTED
    return event_dict


def configure_logging(level: str = "INFO", *, json_output: bool = True) -> None:
    """Configure structlog for the whole process. Call once at startup."""
    renderer: Any = (
        structlog.processors.JSONRenderer() if json_output else structlog.dev.ConsoleRenderer()
    )
    processors: list[Any] = [
        structlog.contextvars.merge_contextvars,  # injects correlation_id
        structlog.processors.add_log_level,
        structlog.processors.TimeStamper(fmt="iso"),
        _redact_phi,
        structlog.processors.StackInfoRenderer(),
        renderer,
    ]
    structlog.configure(
        processors=processors,
        wrapper_class=structlog.make_filtering_bound_logger(logging.getLevelName(level.upper())),
        cache_logger_on_first_use=True,
    )


def get_logger(name: str | None = None) -> structlog.stdlib.BoundLogger:
    return structlog.get_logger(name)
