"""redis.asyncio client factory. Redis is an optimization, never a hard dependency:
callers must treat failures as cache misses (see suggest_service)."""

from redis.asyncio import Redis

from app.core.config import Settings


def create_redis_client(settings: Settings) -> Redis:
    return Redis.from_url(
        settings.redis_url,
        decode_responses=True,
        socket_timeout=0.2,  # cache must be fast or absent — never slow
        socket_connect_timeout=0.2,
    )
