"""Structured JSON logging with a mandatory context envelope.

Every log line carries service/env and, where available, correlation_id / order_id /
symbol so events can be traced across Kafka hops (ARCHITECTURE §2.6).
"""

from __future__ import annotations

import logging
import sys
from typing import Any

import structlog


def configure_logging(service_name: str, env: str, level: str = "INFO") -> None:
    """Configure process-wide structured logging. Call once at startup."""

    logging.basicConfig(format="%(message)s", stream=sys.stdout, level=level.upper())

    structlog.configure(
        processors=[
            structlog.contextvars.merge_contextvars,
            structlog.processors.add_log_level,
            structlog.processors.TimeStamper(fmt="iso", utc=True, key="ts_utc"),
            structlog.processors.StackInfoRenderer(),
            structlog.processors.format_exc_info,
            # JSON in prod for machine ingestion; pretty console in dev.
            structlog.processors.JSONRenderer()
            if env != "dev"
            else structlog.dev.ConsoleRenderer(),
        ],
        wrapper_class=structlog.make_filtering_bound_logger(logging.getLevelName(level.upper())),
        cache_logger_on_first_use=True,
    )
    # Bind the static envelope present on every line from this process.
    structlog.contextvars.bind_contextvars(service=service_name, env=env)


def get_logger(**initial_context: Any) -> structlog.stdlib.BoundLogger:
    """Return a logger, optionally pre-bound with context (e.g. strategy_id)."""
    return structlog.get_logger().bind(**initial_context)
