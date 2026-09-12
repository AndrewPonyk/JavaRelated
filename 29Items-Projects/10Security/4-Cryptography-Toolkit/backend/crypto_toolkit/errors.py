"""Typed service-level exceptions.

Services raise these; the API layer never sees a bare ValueError from crypto
code and the middleware maps every one to the uniform JSON envelope.
"""


class CryptoServiceError(Exception):
    """Base class for all service errors."""

    code = "service_error"
    http_status = 500


class InvalidInputError(CryptoServiceError):
    """Payload failed semantic validation beyond the schema (e.g. bad base64)."""

    code = "invalid_input"
    http_status = 422


class VerificationError(CryptoServiceError):
    """Crypto-semantic failure: bad GCM tag, bad signature, bad password."""

    code = "verification_failed"
    http_status = 422


class UnsupportedOperationError(CryptoServiceError):
    """Requested mode/curve/preset is not allowed (e.g. ECB outside demo)."""

    code = "unsupported_operation"
    http_status = 400


class AuthenticationError(CryptoServiceError):
    """Missing/invalid credentials or token."""

    code = "authentication_failed"
    http_status = 401


class ForbiddenError(CryptoServiceError):
    """Authenticated but not allowed (non-admin hitting admin route)."""

    code = "forbidden"
    http_status = 403


class ConflictError(CryptoServiceError):
    """Resource already exists (duplicate slug/email)."""

    code = "conflict"
    http_status = 409


class NotFoundError(CryptoServiceError):
    """Referenced resource does not exist."""

    code = "not_found"
    http_status = 404
