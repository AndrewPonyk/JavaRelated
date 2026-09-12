"""Query & Admin API application factory.

Run: uvicorn log_analytics.api.main:app --port 8000
Docs: /docs (OpenAPI), ops console: /console/
"""

from __future__ import annotations

import uuid
from pathlib import Path

from fastapi import FastAPI, Request, Response
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles

from log_analytics.api.routers import alert_rules, health, search
from log_analytics.api.services.alert_rule_service import (
    DuplicateRuleNameError,
    RuleNotFoundError,
)
from log_analytics.common.config import get_settings
from log_analytics.common.logging import configure_logging
from log_analytics.common.opensearch import OpenSearchError

# repo-root/frontend/ops-console — same relative layout in a checkout and in the image
# (/app/src/log_analytics/api/main.py → /app/frontend/ops-console).
_CONSOLE_DIR = Path(__file__).resolve().parents[3] / "frontend" / "ops-console"


def create_app() -> FastAPI:
    settings = get_settings()
    configure_logging(service="query-api", level=settings.log_level)

    app = FastAPI(
        title="Log Analytics — Query & Admin API",
        version="0.1.0",
        description="Search logs, manage alert rules. The only reader-facing door to OpenSearch.",
    )

    app.include_router(health.router)
    app.include_router(alert_rules.router, prefix="/api/v1")
    app.include_router(search.router, prefix="/api/v1")

    # ── Cross-cutting middleware ──────────────────────────────────────────

    # Search hits are repetitive JSON — compresses ~10x. Tiny responses stay plain.
    from fastapi.middleware.gzip import GZipMiddleware

    app.add_middleware(GZipMiddleware, minimum_size=1024)

    @app.middleware("http")
    async def request_id(request: Request, call_next) -> Response:
        rid = request.headers.get("X-Request-ID") or uuid.uuid4().hex[:16]
        response: Response = await call_next(request)
        response.headers["X-Request-ID"] = rid
        return response

    if settings.cors_origin_list:
        from fastapi.middleware.cors import CORSMiddleware

        app.add_middleware(
            CORSMiddleware,
            allow_origins=settings.cors_origin_list,
            allow_methods=["GET", "POST", "PUT", "DELETE"],
            allow_headers=["*"],
        )

    # ── Domain error → HTTP mapping (single place) ────────────────────────

    @app.exception_handler(RuleNotFoundError)
    async def rule_not_found(_request: Request, exc: RuleNotFoundError) -> JSONResponse:
        return JSONResponse(status_code=404, content={"detail": str(exc)})

    @app.exception_handler(DuplicateRuleNameError)
    async def duplicate_rule(_request: Request, exc: DuplicateRuleNameError) -> JSONResponse:
        return JSONResponse(status_code=409, content={"detail": str(exc)})

    @app.exception_handler(OpenSearchError)
    async def storage_error(_request: Request, exc: OpenSearchError) -> JSONResponse:
        return JSONResponse(status_code=503, content={"detail": f"storage backend: {exc}"})

    # Serve the zero-build ops console when present (dev convenience; CDN/S3 in prod).
    if _CONSOLE_DIR.is_dir():
        app.mount("/console", StaticFiles(directory=_CONSOLE_DIR, html=True), name="console")

    return app


app = create_app()
