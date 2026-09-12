"""Evidence retention purge (ARCHITECTURE 2.5).

Findings older than EVIDENCE_RETENTION_DAYS keep their row (severity
history survives) but lose raw evidence/response captures, which may
contain captured secrets. Runs at startup and daily thereafter.
"""

from __future__ import annotations

import logging
from datetime import timedelta

from sqlalchemy import update
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.db.base import utcnow
from app.models.finding import Finding

logger = logging.getLogger(__name__)


async def purge_expired_evidence(session: AsyncSession) -> int:
    cutoff = utcnow() - timedelta(days=settings.EVIDENCE_RETENTION_DAYS)
    # Dialect-neutral UPDATE (works on PostgreSQL and the SQLite test DB).
    result = await session.execute(
        update(Finding)
        .where(Finding.created_at < cutoff)
        .values(evidence={}, raw={}, updated_at=utcnow())
    )
    await session.commit()
    purged = result.rowcount or 0
    if purged:
        logger.info(
            "retention purge: stripped evidence from %s findings older than %s days",
            purged,
            settings.EVIDENCE_RETENTION_DAYS,
        )
    return purged
