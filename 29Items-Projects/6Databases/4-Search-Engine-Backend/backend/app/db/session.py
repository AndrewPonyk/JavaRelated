"""Async SQLAlchemy engine & session factory.

Pool sizing note: Cloud Run scales instances, so total PG connections =
pool_size × instances — keep pools modest (TECH-NOTES §3.6 #10).
"""

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.core.config import get_settings

_settings = get_settings()

engine = create_async_engine(
    _settings.database_url,
    pool_size=_settings.database_pool_size,
    pool_pre_ping=True,
    echo=False,
)

async_session_factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
