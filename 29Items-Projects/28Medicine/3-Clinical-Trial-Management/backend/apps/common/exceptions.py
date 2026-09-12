"""Typed domain exceptions + the consistent API error envelope.

Every API error leaves the system in the shape documented in ARCHITECTURE §2.6:

    {"error": {"code": "...", "message": "...", "correlation_id": "...", "details": [...]}}

The message is PHI-free; clients correlate to server logs via ``correlation_id``.
"""
from __future__ import annotations

import logging

from rest_framework import status
from rest_framework.exceptions import APIException
from rest_framework.response import Response
from rest_framework.views import exception_handler as drf_exception_handler

from apps.audit.middleware import get_audit_context

logger = logging.getLogger(__name__)


class DomainError(APIException):
    """Base class for business-rule violations (maps to 4xx, not an alert)."""

    status_code: int = status.HTTP_400_BAD_REQUEST  # subclasses may override (409, 403…)
    default_detail = "A business rule was violated."
    default_code = "DOMAIN_ERROR"


class ConflictError(DomainError):
    status_code = status.HTTP_409_CONFLICT
    default_detail = "The request conflicts with the current state."
    default_code = "CONFLICT"


class EnrollmentError(ConflictError):
    default_detail = "Invalid enrollment state transition."
    default_code = "ENROLLMENT_STATE_INVALID"


class EligibilityError(DomainError):
    default_detail = "Eligibility screening could not be processed."
    default_code = "ELIGIBILITY_ERROR"


def api_exception_handler(exc, context) -> Response | None:
    """Wrap DRF's default handler output in the project error envelope."""
    response = drf_exception_handler(exc, context)
    correlation_id = get_audit_context().correlation_id

    if response is None:
        # Unhandled (non-DRF) exception → 500. Log it; never leak internals.
        logger.exception("Unhandled exception", extra={"correlation_id": correlation_id})
        return Response(
            {
                "error": {
                    "code": "INTERNAL_ERROR",
                    "message": "An unexpected error occurred.",
                    "correlation_id": correlation_id,
                    "details": [],
                }
            },
            status=status.HTTP_500_INTERNAL_SERVER_ERROR,
        )

    code = getattr(exc, "default_code", None) or _code_from_status(response.status_code)
    detail = response.data
    message, details = _split_detail(detail)
    response.data = {
        "error": {
            "code": code.upper(),
            "message": message,
            "correlation_id": correlation_id,
            "details": details,
        }
    }
    return response


def _split_detail(detail):
    """Turn DRF's polymorphic ``detail`` into (message, details[])."""
    if isinstance(detail, dict):
        # The common single-"detail" shape (auth/permission/404) → use it directly.
        if set(detail.keys()) == {"detail"}:
            return str(detail["detail"]), []
        # Field validation errors → keep structured details, generic message.
        return "Validation failed.", [{"field": k, "errors": v} for k, v in detail.items()]
    if isinstance(detail, list):
        return "; ".join(str(d) for d in detail), []
    return str(detail), []


def _code_from_status(code: int) -> str:
    return {
        400: "BAD_REQUEST",
        401: "UNAUTHENTICATED",
        403: "FORBIDDEN",
        404: "NOT_FOUND",
        405: "METHOD_NOT_ALLOWED",
        409: "CONFLICT",
        429: "RATE_LIMITED",
    }.get(code, "ERROR")
