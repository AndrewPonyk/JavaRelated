import socket

import redis
from fastapi import APIRouter, Depends
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.config import settings
from app.db.session import get_db
from app.schemas import HealthRead

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthRead)
async def health_check(db: Session = Depends(get_db)) -> HealthRead:
    database_status = "ok"
    redis_status = "ok"
    kafka_status = "configured"

    try:
        db.execute(text("SELECT 1"))
    except Exception:
        database_status = "unavailable"

    try:
        redis.from_url(settings.redis_url, socket_connect_timeout=0.2).ping()
    except redis.RedisError:
        redis_status = "unavailable"

    host_port = settings.kafka_bootstrap_servers.split(",")[0].split(":")
    if len(host_port) == 2:
        try:
            with socket.create_connection((host_port[0], int(host_port[1])), timeout=0.2):
                kafka_status = "ok"
        except OSError:
            kafka_status = "unavailable"

    overall = "ok" if database_status == "ok" else "degraded"
    return HealthRead(status=overall, database=database_status, redis=redis_status, kafka=kafka_status)
