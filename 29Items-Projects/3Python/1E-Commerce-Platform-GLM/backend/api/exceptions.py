"""
API Exception Handler

Custom exception handler for consistent error responses.
"""

from rest_framework.views import exception_handler
from rest_framework import status
from typing import Any, Dict
import logging

logger = logging.getLogger("backend")


def custom_exception_handler(exc, context) -> Dict[str, Any]:
    """
    Custom exception handler for REST API.

    Returns consistent error response format.
    """
    # Call REST framework's default exception handler first
    response = exception_handler(exc, context)

    if response is not None:
        # Generate error code based on status and exception type
        error_code = _get_error_code(exc, response.status_code)

        # Build custom error response
        custom_response = {
            "error": {
                "code": error_code,
                "message": _get_error_message(exc, response.status_code),
                "details": response.data if response.status_code != 500 else None,
            }
        }

        # Add request ID if available
        request = context.get("request")
        if request:
            request_id = request.META.get("HTTP_X_REQUEST_ID")
            if request_id:
                custom_response["error"]["requestId"] = request_id

        response.data = custom_response

        # Log error
        if response.status_code >= 500:
            logger.error(
                "API error",
                extra={
                    "error_code": error_code,
                    "status_code": response.status_code,
                    "path": request.path if request else None,
                    "method": request.method if request else None,
                    "exc_info": exc,
                },
            )
        elif response.status_code >= 400:
            logger.warning(
                "API client error",
                extra={
                    "error_code": error_code,
                    "status_code": response.status_code,
                    "path": request.path if request else None,
                },
            )

    return response


def _get_error_code(exc, status_code: int) -> str:
    """Generate error code based on exception and status."""
    error_codes = {
        status.HTTP_400_BAD_REQUEST: "VALIDATION_ERROR",
        status.HTTP_401_UNAUTHORIZED: "UNAUTHORIZED",
        status.HTTP_403_FORBIDDEN: "FORBIDDEN",
        status.HTTP_404_NOT_FOUND: "NOT_FOUND",
        status.HTTP_405_METHOD_NOT_ALLOWED: "METHOD_NOT_ALLOWED",
        status.HTTP_409_CONFLICT: "CONFLICT",
        status.HTTP_429_TOO_MANY_REQUESTS: "RATE_LIMIT_EXCEEDED",
        status.HTTP_500_INTERNAL_SERVER_ERROR: "INTERNAL_SERVER_ERROR",
        status.HTTP_503_SERVICE_UNAVAILABLE: "SERVICE_UNAVAILABLE",
    }

    # Check for specific exception types
    from rest_framework.exceptions import ValidationError
    from django.core.exceptions import ValidationError as DjangoValidationError

    if isinstance(exc, (ValidationError, DjangoValidationError)):
        return "VALIDATION_ERROR"

    return error_codes.get(status_code, "UNKNOWN_ERROR")


def _get_error_message(exc, status_code: int) -> str:
    """Get user-friendly error message."""
    # Default messages by status code
    default_messages = {
        status.HTTP_400_BAD_REQUEST: "Invalid request data",
        status.HTTP_401_UNAUTHORIZED: "Authentication required",
        status.HTTP_403_FORBIDDEN: "Permission denied",
        status.HTTP_404_NOT_FOUND: "Resource not found",
        status.HTTP_405_METHOD_NOT_ALLOWED: "Method not allowed",
        status.HTTP_409_CONFLICT: "Resource conflict",
        status.HTTP_429_TOO_MANY_REQUESTS: "Too many requests",
        status.HTTP_500_INTERNAL_SERVER_ERROR: "Internal server error",
        status.HTTP_503_SERVICE_UNAVAILABLE: "Service temporarily unavailable",
    }

    # Use exception detail if available
    if hasattr(exc, "detail"):
        if isinstance(exc.detail, str):
            return exc.detail
        elif isinstance(exc.detail, dict):
            return "Validation error"

    return default_messages.get(status_code, "An error occurred")
