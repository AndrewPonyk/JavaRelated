"""Audit context middleware.

Stashes the current actor, source IP and correlation id in a thread/async-local
so that model signal receivers (which have no access to the request) can attach
them to the ``AuditEvent`` they emit. See ARCHITECTURE.md §2.6.
"""
from __future__ import annotations

import uuid
from collections.abc import Callable
from contextvars import ContextVar
from dataclasses import dataclass

from django.http import HttpRequest, HttpResponse

_CORRELATION_HEADER = "HTTP_X_CORRELATION_ID"


@dataclass(frozen=True)
class AuditContext:
    actor_id: int | None = None
    actor_label: str = ""
    source_ip: str | None = None
    correlation_id: str = ""


# Immutable shared default; per-request contexts are set/reset in the middleware.
_EMPTY = AuditContext()
_ctx: ContextVar[AuditContext | None] = ContextVar("audit_context", default=None)


def get_audit_context() -> AuditContext:
    return _ctx.get() or _EMPTY


class AuditContextMiddleware:
    def __init__(self, get_response: Callable[[HttpRequest], HttpResponse]) -> None:
        self.get_response = get_response

    def __call__(self, request: HttpRequest) -> HttpResponse:
        user = getattr(request, "user", None)
        ctx = AuditContext(
            actor_id=getattr(user, "pk", None) if user and user.is_authenticated else None,
            actor_label=str(user) if user and user.is_authenticated else "anonymous",
            source_ip=self._client_ip(request),
            correlation_id=request.META.get(_CORRELATION_HEADER) or uuid.uuid4().hex,
        )
        token = _ctx.set(ctx)
        try:
            response = self.get_response(request)
        finally:
            _ctx.reset(token)
        # Use the captured context (the ContextVar is already reset above).
        response["X-Correlation-Id"] = ctx.correlation_id
        return response

    @staticmethod
    def _client_ip(request: HttpRequest) -> str | None:
        forwarded = request.META.get("HTTP_X_FORWARDED_FOR")
        if forwarded:
            return forwarded.split(",")[0].strip()
        return request.META.get("REMOTE_ADDR")
