"""
Logging Utilities

Custom logging configuration and utilities.
"""

import logging
import structlog
from typing import Any, Dict
from django.conf import settings


def setup_logging():
    """
    Configure structured logging for the application.
    """
    if settings.app_env == "production":
        log_level = logging.INFO
    else:
        log_level = logging.DEBUG

    # Configure structlog
    structlog.configure(
        processors=[
            structlog.contextvars.merge_contextvars,
            structlog.stdlib.add_logger_name,
            structlog.stdlib.add_log_level,
            structlog.stdlib.PositionalArgumentsFormatter(),
            structlog.processors.TimeStamper(fmt="iso"),
            structlog.processors.StackInfoRenderer(),
            structlog.processors.format_exc_info,
            structlog.processors.UnicodeDecoder(),
            # JSON output in production, text output in development
            structlog.processors.JSONRenderer()
            if settings.app_env == "production"
            else structlog.dev.ConsoleRenderer(),
        ],
        wrapper_class=structlog.stdlib.BoundLogger,
        context_class=dict,
        logger_factory=structlog.stdlib.LoggerFactory(),
        cache_logger_on_first_use=True,
    )


def get_logger(name: str) -> structlog.stdlib.BoundLogger:
    """
    Get a structured logger instance.

    Args:
        name: Logger name (usually __name__)

    Returns:
        Structlog logger instance
    """
    return structlog.get_logger(name)


class LogContext:
    """
    Context manager for adding structured log context.

    Example:
        with LogContext(user_id=user.id, request_id=request_id):
            logger.info("Processing request")
    """

    def __init__(self, **kwargs):
        self.context = kwargs

    def __enter__(self):
        structlog.contextvars.bind_contextvars(**self.context)
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        structlog.contextvars.unbind_contextvars(*self.context.keys())


def log_exception(logger: structlog.stdlib.BoundLogger, exc: Exception, **kwargs):
    """
    Log an exception with structured context.

    Args:
        logger: The logger instance
        exc: The exception to log
        **kwargs: Additional context to log
    """
    logger.exception(
        "exception_occurred",
        exception_type=type(exc).__name__,
        exception_message=str(exc),
        **kwargs,
    )
