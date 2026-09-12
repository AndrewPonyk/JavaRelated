"""Audit service — writes the append-only PHI access trail (HIPAA §164.312(b))."""

from __future__ import annotations

import structlog
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.logging import get_logger
from app.models.audit import AuditLog

log = get_logger(__name__)


class AuditService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def record(
        self,
        *,
        actor_id: str | None,
        action: str,
        resource_type: str,
        resource_id: str | None = None,
        outcome: str = "allow",
        source_ip: str | None = None,
    ) -> AuditLog:
        """Persist one audit row. correlation_id is pulled from log context."""
        ctx = structlog.contextvars.get_contextvars()
        entry = AuditLog(
            actor_id=actor_id,
            action=action,
            resource_type=resource_type,
            resource_id=resource_id,
            outcome=outcome,
            source_ip=source_ip,
            correlation_id=ctx.get("correlation_id"),
        )
        self._session.add(entry)
        await self._session.flush()
        log.info(
            "audit",
            actor_id=actor_id,
            action=action,
            resource_type=resource_type,
            resource_id=resource_id,
            outcome=outcome,
        )
        return entry
