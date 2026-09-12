"""First-run bootstrap: create the admin user if none exists."""

from __future__ import annotations

import logging

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.security import hash_password
from app.models.user import User

logger = logging.getLogger(__name__)


async def seed_admin(session: AsyncSession) -> None:
    """Idempotent — inserts the configured admin only when no admin exists."""
    admin_count = await session.scalar(select(func.count(User.id)).where(User.role == "admin"))
    if admin_count:
        return
    session.add(
        User(
            email=settings.ADMIN_EMAIL,
            full_name="Bootstrap Admin",
            hashed_password=hash_password(settings.ADMIN_PASSWORD),
            role="admin",
        )
    )
    await session.commit()
    logger.info("seeded bootstrap admin %s", settings.ADMIN_EMAIL)
