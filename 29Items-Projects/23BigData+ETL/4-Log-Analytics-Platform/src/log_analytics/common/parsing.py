"""Pure parsing/normalization helpers shared by the gateway, Spark UDFs and offline tooling.

No third-party imports — this module must be trivially serializable to Spark executors.
"""

from __future__ import annotations

import hashlib
import json
import re
from datetime import datetime, timezone
from typing import Any

# ── Level normalization ──────────────────────────────────────────────────────

_LEVEL_ALIASES: dict[str, str] = {
    "TRACE": "TRACE",
    "VERBOSE": "TRACE",
    "DEBUG": "DEBUG",
    "INFO": "INFO",
    "INFORMATION": "INFO",
    "NOTICE": "INFO",
    "WARN": "WARN",
    "WARNING": "WARN",
    "ERR": "ERROR",
    "ERROR": "ERROR",
    "SEVERE": "ERROR",
    "FATAL": "FATAL",
    "CRIT": "FATAL",
    "CRITICAL": "FATAL",
    "EMERG": "FATAL",
    "PANIC": "FATAL",
}


def normalize_level(raw: str | None, default: str = "INFO") -> str:
    """Map the zoo of logging-framework level names onto the canonical six."""
    if not raw:
        return default
    return _LEVEL_ALIASES.get(raw.strip().upper(), default)


# ── Timestamp parsing ────────────────────────────────────────────────────────

_TS_FORMATS: tuple[str, ...] = (
    "%Y-%m-%d %H:%M:%S,%f",  # log4j / logback default
    "%Y-%m-%d %H:%M:%S.%f",
    "%Y-%m-%d %H:%M:%S",
    "%d/%b/%Y:%H:%M:%S %z",  # apache/nginx access logs
    "%b %d %H:%M:%S",  # syslog (no year!)
)

# Epoch heuristics: seconds ~1e9, millis ~1e12, micros ~1e15 for contemporary dates.
_EPOCH_MILLIS_MIN = 1e11
_EPOCH_MICROS_MIN = 1e14


def parse_timestamp(value: Any) -> datetime | None:
    """Best-effort timestamp parsing; returns timezone-aware UTC or None if hopeless."""
    if value is None:
        return None
    if isinstance(value, datetime):
        return value if value.tzinfo else value.replace(tzinfo=timezone.utc)
    if isinstance(value, int | float):
        return _from_epoch(float(value))
    if not isinstance(value, str):
        return None

    text = value.strip()
    if not text:
        return None

    # Numeric string → epoch.
    try:
        return _from_epoch(float(text))
    except ValueError:
        pass

    # ISO-8601 (the overwhelmingly common case).
    try:
        parsed = datetime.fromisoformat(text.replace("Z", "+00:00"))
        return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)
    except ValueError:
        pass

    for fmt in _TS_FORMATS:
        try:
            parsed = datetime.strptime(text, fmt)
            if parsed.year == 1900:  # syslog format carries no year
                parsed = parsed.replace(year=datetime.now(timezone.utc).year)
            return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)
        except ValueError:
            continue
    return None


def _from_epoch(value: float) -> datetime | None:
    try:
        if value >= _EPOCH_MICROS_MIN:
            value /= 1_000_000.0
        elif value >= _EPOCH_MILLIS_MIN:
            value /= 1000.0
        return datetime.fromtimestamp(value, tz=timezone.utc)
    except (OverflowError, OSError, ValueError):
        return None


# ── Template mining & redaction ──────────────────────────────────────────────
#
# Patterns are kept as (pattern, replacement) string pairs valid in BOTH Python `re`
# and Java regex, because the enrichment Spark job applies them via regexp_replace
# on executors while Python code (tests, offline tooling) applies them via `re`.

TEMPLATE_PATTERNS: tuple[tuple[str, str], ...] = (
    # order matters: uuid before generic hex before numbers
    (r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "<uuid>"),
    (r"\b[0-9a-fA-F]{16,}\b", "<hex>"),
    # no trailing \b: numbers glued to units ("56.7ms") must mask fully
    (r"\b\d+(?:\.\d+)?", "<num>"),
    (r"'[^']*'", "<str>"),
    (r'"[^"]*"', "<str>"),
)

REDACTION_PATTERNS: tuple[tuple[str, str], ...] = (
    (r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", "<email>"),
    (r"(?i)bearer\s+[A-Za-z0-9._~+/=-]{8,}", "bearer <redacted>"),
    (r"(?i)\b(password|passwd|secret|token|api[_-]?key)\s*[=:]\s*\S+", "$1=<redacted>"),
)


def redact(message: str) -> str:
    """Scrub known PII/secret patterns. Same patterns the Spark enrichment applies."""
    for pattern, replacement in REDACTION_PATTERNS:
        # Java-style `$1` group refs → Python `\1`.
        message = re.sub(pattern, replacement.replace("$1", r"\1"), message)
    return message


def template_of(message: str, max_length: int = 200) -> str:
    """Collapse a log message to its structural template (numbers/ids/strings masked).

    A lightweight stand-in for Drain-style template mining: good enough to group
    'order 1234 failed' with 'order 5678 failed' for pattern analytics and features.
    """
    templated = message
    for pattern, replacement in TEMPLATE_PATTERNS:
        templated = re.sub(pattern, replacement, templated)
    templated = re.sub(r"\s+", " ", templated).strip()
    return templated[:max_length]


def template_id(message: str) -> str:
    """Stable 16-hex-char id of the message template (the `template_id` keyword field)."""
    return hashlib.sha1(template_of(message).encode("utf-8")).hexdigest()[:16]


# ── Record shape coercion ────────────────────────────────────────────────────

# Producers name things differently; map the common aliases onto the canonical schema.
_FIELD_ALIASES: dict[str, tuple[str, ...]] = {
    "timestamp": ("timestamp", "@timestamp", "time", "ts", "datetime"),
    "level": ("level", "severity", "log_level", "lvl", "loglevel"),
    "message": ("message", "msg", "log", "text"),
    "service": ("service", "service_name", "app", "application", "component"),
    "env": ("env", "environment", "stage"),
    "host": ("host", "hostname", "instance", "node"),
    "trace_id": ("trace_id", "traceid", "traceId"),
    "span_id": ("span_id", "spanid", "spanId"),
}

_KNOWN_SOURCE_KEYS = frozenset(alias for aliases in _FIELD_ALIASES.values() for alias in aliases)


def try_parse_json_line(line: str) -> dict[str, Any] | None:
    """Return the parsed object if `line` is a JSON object, else None (plain-text log line)."""
    stripped = line.strip()
    if not stripped.startswith("{"):
        return None
    try:
        parsed = json.loads(stripped)
    except json.JSONDecodeError:
        return None
    return parsed if isinstance(parsed, dict) else None


def coerce_log_record(raw: dict[str, Any]) -> dict[str, Any]:
    """Normalize an arbitrary producer payload into canonical LogEvent field names.

    Unrecognized keys are preserved under `attributes` instead of being dropped —
    they end up in the flat_object mapping, never as dynamic top-level fields.
    """
    record: dict[str, Any] = {}
    for canonical, aliases in _FIELD_ALIASES.items():
        for alias in aliases:
            if alias in raw and raw[alias] not in (None, ""):
                record[canonical] = raw[alias]
                break

    extras = {k: v for k, v in raw.items() if k not in _KNOWN_SOURCE_KEYS and k != "attributes"}
    attributes = raw.get("attributes")
    if isinstance(attributes, dict):
        extras = {**attributes, **extras}
    if extras:
        record["attributes"] = extras
    return record
