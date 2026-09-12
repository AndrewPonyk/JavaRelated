"""FastAPI application factory.

Wires logging, LangSmith tracing, request-id correlation, exception handlers, and the
versioned API router. Run with ``uvicorn app.main:app --reload``.
"""

from __future__ import annotations

import logging
import os
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import JSONResponse

from app import __version__
from app.api.v1.router import api_router
from app.core.config import settings
from app.core.logging import configure_logging, request_id_ctx
from app.rag.errors import RAGError

logger = logging.getLogger(__name__)


def _configure_langsmith() -> None:
    """Enable LangSmith tracing via env vars when configured (no-op otherwise)."""
    if settings.langsmith_tracing and settings.langsmith_api_key:
        os.environ.setdefault("LANGSMITH_TRACING", "true")
        os.environ.setdefault("LANGSMITH_API_KEY", settings.langsmith_api_key)
        os.environ.setdefault("LANGSMITH_PROJECT", settings.langsmith_project)


@asynccontextmanager
async def lifespan(app: FastAPI):
    configure_logging(settings.log_level)
    _configure_langsmith()
    yield


def create_app() -> FastAPI:
    app = FastAPI(
        title="LLM-Powered Document Intelligence",
        version=__version__,
        lifespan=lifespan,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    # Compress JSON/SSE payloads over ~1 KB.
    app.add_middleware(GZipMiddleware, minimum_size=1024)

    @app.middleware("http")
    async def add_request_id(request: Request, call_next):
        rid = request.headers.get("x-request-id", str(uuid.uuid4()))
        token = request_id_ctx.set(rid)
        try:
            response = await call_next(request)
        finally:
            request_id_ctx.reset(token)
        response.headers["x-request-id"] = rid
        return response

    @app.exception_handler(RAGError)
    async def handle_rag_error(_: Request, exc: RAGError) -> JSONResponse:
        # Domain errors map to stable HTTP shapes — clients never see stack traces.
        logger.warning("RAG error: %s", exc)
        return JSONResponse(
            status_code=502,
            content={"detail": str(exc), "type": exc.__class__.__name__},
        )

    @app.exception_handler(Exception)
    async def handle_unexpected(request: Request, exc: Exception) -> JSONResponse:
        # Last-resort handler: log the full error server-side, return an opaque 500 so
        # internals / stack traces never reach the client.
        logger.exception("unhandled error on %s %s", request.method, request.url.path)
        return JSONResponse(
            status_code=500,
            content={"detail": "internal server error", "request_id": request_id_ctx.get()},
        )

    app.include_router(api_router, prefix=settings.api_v1_prefix)
    return app


app = create_app()
