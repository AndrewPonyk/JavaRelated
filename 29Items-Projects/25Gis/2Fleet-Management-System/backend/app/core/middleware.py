from collections.abc import Callable
from uuid import uuid4

from fastapi import Request, Response

from app.core.config import settings


async def request_id_middleware(request: Request, call_next: Callable) -> Response:
    request_id = request.headers.get(settings.request_id_header, str(uuid4()))
    request.state.request_id = request_id
    response = await call_next(request)
    response.headers[settings.request_id_header] = request_id
    return response


async def security_headers_middleware(request: Request, call_next: Callable) -> Response:
    response = await call_next(request)
    if settings.enable_security_headers:
        response.headers.setdefault("X-Content-Type-Options", "nosniff")
        response.headers.setdefault("X-Frame-Options", "DENY")
        response.headers.setdefault("Referrer-Policy", "strict-origin-when-cross-origin")
        response.headers.setdefault("Permissions-Policy", "geolocation=(), microphone=(), camera=()")
        if settings.environment.lower() == "production":
            response.headers.setdefault(
                "Strict-Transport-Security",
                "max-age=31536000; includeSubDomains",
            )
    return response
