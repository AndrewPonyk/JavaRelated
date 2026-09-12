import time
from collections.abc import Awaitable, Callable
from uuid import uuid4

from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware

from app.core.config import get_settings


class RequestContextMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next: Callable[[Request], Awaitable[Response]]) -> Response:
        settings = get_settings()
        request_id = request.headers.get(settings.request_id_header, str(uuid4()))
        start = time.perf_counter()

        response = await call_next(request)
        response.headers[settings.request_id_header] = request_id
        response.headers["X-Response-Time-Ms"] = f"{(time.perf_counter() - start) * 1000:.2f}"
        return response


class SecurityHeadersMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next: Callable[[Request], Awaitable[Response]]) -> Response:
        settings = get_settings()
        forwarded_proto = request.headers.get("x-forwarded-proto", request.url.scheme)
        if settings.enforce_https and forwarded_proto != "https":
            host = request.headers.get("host", request.url.netloc)
            return Response(status_code=308, headers={"Location": f"https://{host}{request.url.path}"})

        response = await call_next(request)
        response.headers.setdefault("X-Content-Type-Options", "nosniff")
        response.headers.setdefault("X-Frame-Options", "DENY")
        response.headers.setdefault("Referrer-Policy", "strict-origin-when-cross-origin")
        docs_csp = (
            "default-src 'self'; script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
            "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; img-src 'self' data: https://fastapi.tiangolo.com; "
            "frame-ancestors 'none'; object-src 'none'; base-uri 'self'"
        )
        api_csp = "default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'"
        response.headers.setdefault(
            "Content-Security-Policy",
            docs_csp if request.url.path in {"/docs", "/redoc"} else api_csp,
        )
        if settings.enforce_https:
            response.headers.setdefault("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        return response
