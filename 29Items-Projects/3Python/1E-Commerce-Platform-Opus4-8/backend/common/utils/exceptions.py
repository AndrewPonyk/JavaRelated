"""Standard error envelope + domain exceptions.

Every API error returns the same JSON shape (ARCHITECTURE.md §2.6):

    {"error": {"code", "message", "details", "request_id"}}
"""

from __future__ import annotations

from typing import Any

from rest_framework.response import Response
from rest_framework.views import exception_handler as drf_exception_handler


class DomainError(Exception):
    """Base class for expected business-rule violations (mapped to 4xx)."""

    status_code = 400
    code = "DOMAIN_ERROR"

    def __init__(self, message: str, details: dict[str, Any] | None = None) -> None:
        super().__init__(message)
        self.message = message
        self.details = details or {}


class InventoryInsufficientError(DomainError):
    status_code = 409
    code = "INVENTORY_INSUFFICIENT"


class CartEmptyError(DomainError):
    status_code = 422
    code = "CART_EMPTY"


def standard_exception_handler(exc: Exception, context: dict[str, Any]) -> Response | None:
    """DRF exception handler that wraps every error in the standard envelope."""
    request = context.get("request")
    request_id = getattr(request, "request_id", None)

    if isinstance(exc, DomainError):
        return Response(
            {
                "error": {
                    "code": exc.code,
                    "message": exc.message,
                    "details": exc.details,
                    "request_id": request_id,
                }
            },
            status=exc.status_code,
        )

    # Fall back to DRF's handling, then reshape into our envelope.
    response = drf_exception_handler(exc, context)
    if response is not None:
        response.data = {
            "error": {
                "code": getattr(exc, "default_code", "ERROR"),
                "message": _flatten(response.data),
                "details": response.data,
                "request_id": request_id,
            }
        }
    return response


def _flatten(data: Any) -> str:
    if isinstance(data, dict):
        return "; ".join(f"{k}: {_flatten(v)}" for k, v in data.items())
    if isinstance(data, list | tuple):
        return "; ".join(_flatten(v) for v in data)
    return str(data)
