"""
Custom Exception Classes
"""
from typing import Optional, Dict, Any


class AppException(Exception):
    """
    Base exception class for application errors.

    Provides consistent error response format.
    """
    status_code: int = 500
    error_code: str = 'INTERNAL_ERROR'
    message: str = 'An unexpected error occurred'

    def __init__(
        self,
        message: Optional[str] = None,
        details: Optional[Dict[str, Any]] = None,
        status_code: Optional[int] = None
    ):
        super().__init__(message or self.message)
        if message:
            self.message = message
        if status_code:
            self.status_code = status_code
        self.details = details or {}

    def to_dict(self) -> Dict[str, Any]:
        """Convert exception to dictionary for JSON response."""
        response = {
            'error': {
                'code': self.error_code,
                'message': self.message
            }
        }
        if self.details:
            response['error']['details'] = self.details
        return response


class ValidationError(AppException):
    """Raised when input validation fails."""
    status_code = 400
    error_code = 'VALIDATION_ERROR'
    message = 'Invalid input data'


class NotFoundError(AppException):
    """Raised when a requested resource is not found."""
    status_code = 404
    error_code = 'NOT_FOUND'
    message = 'Resource not found'


class AuthenticationError(AppException):
    """Raised when authentication fails."""
    status_code = 401
    error_code = 'AUTHENTICATION_ERROR'
    message = 'Authentication required'


class AuthorizationError(AppException):
    """Raised when user lacks required permissions."""
    status_code = 403
    error_code = 'AUTHORIZATION_ERROR'
    message = 'Permission denied'


class ConflictError(AppException):
    """Raised when there's a resource conflict (e.g., duplicate email)."""
    status_code = 409
    error_code = 'CONFLICT'
    message = 'Resource conflict'


class ExternalServiceError(AppException):
    """Raised when an external service fails."""
    status_code = 503
    error_code = 'EXTERNAL_SERVICE_ERROR'
    message = 'External service unavailable'


class RateLimitError(AppException):
    """Raised when rate limit is exceeded."""
    status_code = 429
    error_code = 'RATE_LIMIT_EXCEEDED'
    message = 'Too many requests'
