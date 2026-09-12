"""Error taxonomy + the single generic catch point for the UI.

Raise typed errors anywhere below the UI; catch them ONLY in @guard_page.
``user_message`` must be safe and actionable for an analyst; technical detail
belongs in the log line. See docs/ARCHITECTURE.md §2.6.
"""

from __future__ import annotations

import functools
from collections.abc import Callable
from typing import Any, TypeVar

from app.core.logging import get_logger

logger = get_logger(__name__)

F = TypeVar("F", bound=Callable[..., Any])


class AppError(Exception):
    """Base application error. ``user_message`` is safe to render in the UI."""

    default_user_message = "Something went wrong. Please try again."

    def __init__(self, message: str | None = None, *, user_message: str | None = None) -> None:
        super().__init__(message or self.default_user_message)
        self.user_message = user_message or message or self.default_user_message


class DataValidationError(AppError):
    """The analyst's input is at fault (bad CSV, wrong column choice) — they can fix it."""

    default_user_message = "The provided data is invalid. Check the file format and try again."


class AnalysisError(AppError):
    """The statistics cannot proceed (n too small, zero variance, unmet preconditions)."""

    default_user_message = "The analysis could not be completed with this data."


class StorageError(AppError):
    """Infrastructure trouble (database unreachable / misconfigured)."""

    default_user_message = (
        "Storage is currently unavailable. Your analysis still ran; results were not saved."
    )


def _is_streamlit_control_flow(exc: BaseException) -> bool:
    """st.rerun()/st.stop() signal via exceptions — they are navigation, not errors.

    Matched by module+name so this works across Streamlit versions without
    importing private paths.
    """
    return type(exc).__module__.startswith("streamlit") and type(exc).__name__ in (
        "RerunException",
        "StopException",
    )


def guard_page(fn: F) -> F:
    """Wrap a page's render(): expected errors -> friendly st.error; unexpected -> log + apology.

    Streamlit is imported lazily so this module stays importable in Streamlit-free
    contexts (unit tests, future service extraction). Streamlit's own control-flow
    exceptions (rerun/stop) pass through untouched.
    """

    @functools.wraps(fn)
    def wrapper(*args: Any, **kwargs: Any) -> Any:
        import streamlit as st

        try:
            return fn(*args, **kwargs)
        except AppError as exc:
            logger.warning("Handled %s in %s: %s", type(exc).__name__, fn.__module__, exc)
            st.error(exc.user_message, icon="⚠️")
        except Exception as exc:
            if _is_streamlit_control_flow(exc):
                raise
            logger.exception("Unhandled error in %s", fn.__module__)
            _capture_exception()
            st.error("Unexpected error. The details have been logged.", icon="🛑")

    return wrapper  # type: ignore[return-value]


def _capture_exception() -> None:
    """Forward the active exception to Sentry when the SDK is configured.

    A no-op (never a failure) when sentry-sdk is absent or uninitialized —
    error tracking must not be able to break error handling.
    """
    try:
        import sentry_sdk

        sentry_sdk.capture_exception()
    except Exception:
        pass
