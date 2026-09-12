"""Structured logging configuration.

Uses structlog with a JSON renderer when available and falls back to the
standard library ``logging`` module otherwise. Request-scoped context (the
request id) is bound via structlog contextvars so every log line emitted
while handling a request carries the correlation id.
"""

from __future__ import annotations

import logging
import sys
from typing import Any


def configure_logging(level: str = "INFO") -> None:
    """Configure application-wide structured (JSON) logging.

    Args:
        level: Log level name (e.g. ``"INFO"``, ``"DEBUG"``).
    """
    numeric_level = getattr(logging, level.upper(), logging.INFO)
    logging.basicConfig(format="%(message)s", stream=sys.stdout, level=numeric_level, force=True)
    try:
        import structlog

        structlog.configure(
            processors=[
                structlog.contextvars.merge_contextvars,
                structlog.processors.add_log_level,
                structlog.processors.TimeStamper(fmt="iso", utc=True),
                structlog.processors.StackInfoRenderer(),
                structlog.processors.format_exc_info,
                structlog.processors.JSONRenderer(),
            ],
            wrapper_class=structlog.make_filtering_bound_logger(numeric_level),
            logger_factory=structlog.PrintLoggerFactory(sys.stdout),
            cache_logger_on_first_use=True,
        )
    except ImportError:  # pragma: no cover - structlog is expected in all environments
        logging.getLogger(__name__).warning("structlog not installed; using stdlib logging")


def get_logger(name: str) -> Any:
    """Return a structlog logger when available, otherwise a stdlib logger."""
    try:
        import structlog

        return structlog.get_logger(name)
    except ImportError:  # pragma: no cover
        return logging.getLogger(name)


def bind_request_id(request_id: str) -> None:
    """Bind the request correlation id to the logging context."""
    try:
        import structlog

        structlog.contextvars.bind_contextvars(request_id=request_id)
    except ImportError:  # pragma: no cover
        pass


def clear_request_context() -> None:
    """Clear request-scoped logging context at the end of a request."""
    try:
        import structlog

        structlog.contextvars.clear_contextvars()
    except ImportError:  # pragma: no cover
        pass
