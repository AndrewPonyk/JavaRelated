"""Async Neo4j driver lifecycle.

Create one driver per process at startup and reuse its connection pool;
never open a driver per request.
"""

from neo4j import AsyncDriver, AsyncGraphDatabase

from app.core.config import get_settings
from app.core.logging import get_logger

logger = get_logger(__name__)

_driver: AsyncDriver | None = None


async def init_driver() -> AsyncDriver:
    global _driver
    if _driver is None:
        settings = get_settings()
        _driver = AsyncGraphDatabase.driver(
            settings.neo4j_uri,
            auth=(settings.neo4j_user, settings.neo4j_password),
            max_connection_pool_size=settings.neo4j_max_pool_size,
        )
        logger.info("neo4j_driver_initialized", uri=settings.neo4j_uri)
    return _driver


def get_driver() -> AsyncDriver:
    if _driver is None:
        raise RuntimeError("Neo4j driver not initialized; call init_driver() first.")
    return _driver


async def close_driver() -> None:
    global _driver
    if _driver is not None:
        await _driver.close()
        _driver = None
        logger.info("neo4j_driver_closed")


async def verify_connectivity() -> bool:
    try:
        await get_driver().verify_connectivity()
        return True
    except Exception as exc:  # noqa: BLE001 - report any connectivity failure
        logger.error("neo4j_connectivity_failed", error=str(exc))
        return False
