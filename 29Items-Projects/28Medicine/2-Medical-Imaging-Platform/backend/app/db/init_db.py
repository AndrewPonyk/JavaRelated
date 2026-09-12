"""Bootstrap helpers: ensure object-store buckets and seed dev users.

`startup_bootstrap()` runs from the app lifespan (best-effort, never fatal).
Running this module directly performs the same steps for manual setup.
"""

from __future__ import annotations

from sqlalchemy import func, select

from app.core.config import settings
from app.core.logging import get_logger
from app.core.security import Role
from app.db.session import SessionFactory
from app.models.user import User
from app.services.storage_service import get_storage_service
from app.services.user_service import UserService

log = get_logger(__name__)

# Default dev accounts (seeded only outside production, only if users table empty).
_DEV_USERS = [
    ("admin@medimaging.local", "admin12345", "Platform Admin", Role.ADMIN),
    ("radiologist@medimaging.local", "radiology123", "Dr. Radiologist", Role.RADIOLOGIST),
]


async def ensure_buckets() -> None:
    try:
        get_storage_service().ensure_buckets()
        log.info("bootstrap.buckets_ready")
    except Exception as exc:  # noqa: BLE001 - best-effort at startup
        log.warning("bootstrap.buckets_failed", error=str(exc))


async def seed_dev_users() -> None:
    if settings.is_production:
        return
    try:
        async with SessionFactory() as session:
            count = await session.scalar(select(func.count(User.id)))
            if count and count > 0:
                return
            svc = UserService(session)
            for email, password, name, role in _DEV_USERS:
                await svc.create(email=email, password=password, full_name=name, role=role)
            await session.commit()
            log.info("bootstrap.users_seeded", count=len(_DEV_USERS))
    except Exception as exc:  # noqa: BLE001 - tables may not exist yet; non-fatal
        log.warning("bootstrap.seed_failed", error=str(exc))


async def startup_bootstrap() -> None:
    await ensure_buckets()
    await seed_dev_users()


if __name__ == "__main__":  # pragma: no cover
    import asyncio

    asyncio.run(startup_bootstrap())
