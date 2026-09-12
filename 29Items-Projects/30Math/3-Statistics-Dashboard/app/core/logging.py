"""Idempotent stdout logging + optional error tracking.

Streamlit re-executes the entrypoint on every widget interaction; without the
guard each rerun would stack another handler and duplicate every log line
(docs/TECH-NOTES.md §3.6.2).

In production (APP_ENV=production) lines are emitted as JSON so the platform's
log collector can index them; in development they stay human-readable.
Logs never include cell values — only shapes, dtypes, and timings
(docs/ARCHITECTURE.md §2.6).
"""

from __future__ import annotations

import json
import logging
import sys
from datetime import UTC, datetime

_CONFIGURED = False


class _JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, object] = {
            "ts": datetime.fromtimestamp(record.created, tz=UTC).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        if record.exc_info:
            payload["exc_info"] = self.formatException(record.exc_info)
        return json.dumps(payload, ensure_ascii=False)


def _maybe_init_sentry() -> None:
    """Initialize Sentry when a DSN is configured and the SDK is installed.

    sentry-sdk is an optional dependency: absence is a supported configuration,
    so both the import and the init are guarded.
    """
    from app.core.config import get_settings

    settings = get_settings()
    if not settings.sentry_dsn:
        return
    try:
        import sentry_sdk

        sentry_sdk.init(dsn=settings.sentry_dsn, environment=settings.app_env)
        logging.getLogger(__name__).info("Sentry initialized (env=%s)", settings.app_env)
    except ImportError:
        logging.getLogger(__name__).warning(
            "SENTRY_DSN is set but sentry-sdk is not installed — error tracking disabled."
        )


def setup_logging(level: str | None = None) -> None:
    global _CONFIGURED
    if _CONFIGURED:
        return

    from app.core.config import get_settings

    settings = get_settings()
    resolved = (level or settings.log_level).upper()

    handler = logging.StreamHandler(sys.stdout)  # Streamlit Cloud and Docker collect stdout
    if settings.app_env == "production":
        handler.setFormatter(_JsonFormatter())
    else:
        handler.setFormatter(
            logging.Formatter(
                "%(asctime)s %(levelname)-7s %(name)s :: %(message)s",
                datefmt="%Y-%m-%d %H:%M:%S",
            )
        )
    root = logging.getLogger()
    root.setLevel(resolved)
    root.addHandler(handler)

    # Quiet chatty dependencies; our own loggers stay at the configured level.
    for noisy in ("watchdog", "urllib3", "PIL", "matplotlib"):
        logging.getLogger(noisy).setLevel(logging.WARNING)

    _maybe_init_sentry()
    _CONFIGURED = True


def get_logger(name: str) -> logging.Logger:
    return logging.getLogger(name)
