class SaaSPlatformException(Exception):
    """Base exception for all SaaS platform domain errors."""

    def __init__(self, message: str, status_code: int = 500, error_code: str = "INTERNAL_ERROR"):
        super().__init__(message)
        self.message = message
        self.status_code = status_code
        self.error_code = error_code


class TenantNotFoundException(SaaSPlatformException):
    def __init__(self, tenant_id: str):
        super().__init__(
            message=f"Tenant '{tenant_id}' was not found.",
            status_code=404,
            error_code="TENANT_NOT_FOUND",
        )


class GeolocationAccessDeniedException(SaaSPlatformException):
    def __init__(self, country_code: str, allowed_countries: list[str]):
        super().__init__(
            message=f"Access denied from country '{country_code}'. Allowed countries: {allowed_countries}",
            status_code=403,
            error_code="GEOLOCATION_RESTRICTED",
        )


class QuotaExceededException(SaaSPlatformException):
    def __init__(self, tenant_id: str, metric: str, current_usage: int, limit: int):
        super().__init__(
            message=f"Tenant '{tenant_id}' has exceeded quota for metric '{metric}': {current_usage}/{limit}.",
            status_code=429,
            error_code="QUOTA_EXCEEDED",
        )


class ValidationException(SaaSPlatformException):
    def __init__(self, message: str):
        super().__init__(
            message=message,
            status_code=400,
            error_code="VALIDATION_ERROR",
        )
