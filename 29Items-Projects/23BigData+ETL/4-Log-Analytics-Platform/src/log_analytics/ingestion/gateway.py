"""HTTP ingestion gateway: validated batch intake → Kafka `logs.raw`.

Design decisions:
- Per-item validation: one malformed event never rejects the whole batch; the response
  reports accepted/rejected counts so producers can alarm on their own bad payloads.
- The gateway is stateless — durability starts at Kafka (acks=all).
- Bodies may be gzip-compressed (Content-Encoding: gzip) — log agents batch aggressively.
- Optional per-caller rate limiting (token bucket, events/sec) when LA_GATEWAY_RATE_LIMIT_RPS > 0.

Run: uvicorn log_analytics.ingestion.gateway:app --port 8080
"""

from __future__ import annotations

import gzip
import hmac
import io
import json
import time
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Annotated, Any

from aiokafka.errors import KafkaError
from fastapi import Depends, FastAPI, Header, HTTPException, Request, status
from pydantic import BaseModel, ValidationError

from log_analytics.common.config import Settings, get_settings
from log_analytics.common.logging import configure_logging
from log_analytics.common.models import LogEvent
from log_analytics.common.parsing import coerce_log_record
from log_analytics.ingestion.producer import LogEventProducer

logger = configure_logging(service="ingestion-gateway")

_MAX_BODY_BYTES = 32 * 1024 * 1024  # decompressed cap — a batch should never be this big

_producer: LogEventProducer | None = None
_limiter: RateLimiter | None = None


class RateLimiter:
    """Token bucket per caller key (API key or client IP), measured in *events*."""

    def __init__(self, rate_per_second: float, burst: int) -> None:
        self._rate = rate_per_second
        self._burst = float(burst)
        self._buckets: dict[str, tuple[float, float]] = {}  # key -> (tokens, last_ts)

    def try_consume(self, key: str, tokens: int) -> bool:
        now = time.monotonic()
        level, last = self._buckets.get(key, (self._burst, now))
        level = min(self._burst, level + (now - last) * self._rate)
        if tokens > level:
            self._buckets[key] = (level, now)
            return False
        self._buckets[key] = (level - tokens, now)
        return True


def _get_limiter(settings: Settings) -> RateLimiter | None:
    global _limiter
    if settings.gateway_rate_limit_rps <= 0:
        return None
    if _limiter is None:
        _limiter = RateLimiter(settings.gateway_rate_limit_rps, settings.gateway_rate_limit_burst)
    return _limiter


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    global _producer
    settings = get_settings()
    _producer = LogEventProducer(
        settings.kafka_bootstrap_servers,
        security_protocol=settings.kafka_security_protocol,
        sasl_mechanism=settings.kafka_sasl_mechanism,
        sasl_username=settings.kafka_sasl_username,
        sasl_password=settings.kafka_sasl_password,
    )
    await _producer.start()
    try:
        yield
    finally:
        await _producer.stop()
        _producer = None


app = FastAPI(
    title="Log Analytics — Ingestion Gateway",
    version="0.1.0",
    lifespan=lifespan,
)


class IngestResponse(BaseModel):
    accepted: int
    rejected: int
    errors: list[str] = []  # first N validation errors, for producer-side debugging


def verify_api_key(
    settings: Annotated[Settings, Depends(get_settings)],
    x_api_key: Annotated[str | None, Header()] = None,
) -> str | None:
    """API-key check; returns the caller identity used for rate limiting.

    Empty key set = auth disabled (local dev only — enforced empty in prod tfvars).
    Comparison is constant-time per key so response timing can't confirm partial guesses.
    """
    keys = settings.gateway_api_key_set
    if keys:
        supplied = x_api_key or ""
        if not any(hmac.compare_digest(supplied, key) for key in keys):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid API key")
    return x_api_key


async def _read_batch(request: Request, max_batch: int) -> list[dict[str, Any]]:
    """Read the request body (gzip-aware) and return the event batch.

    Both reads are size-capped *while* reading: the wire body is consumed in chunks and
    aborted past the cap (a Content-Length lie or an unbounded stream can't buffer GBs),
    and gzip inflation is bounded (a decompression bomb stops at the cap, not at OOM).
    """
    chunks = bytearray()
    async for chunk in request.stream():
        chunks.extend(chunk)
        if len(chunks) > _MAX_BODY_BYTES:
            raise HTTPException(status_code=413, detail="body too large")
    body = bytes(chunks)
    if request.headers.get("content-encoding", "").lower() == "gzip":
        try:
            with gzip.GzipFile(fileobj=io.BytesIO(body)) as stream:
                body = stream.read(_MAX_BODY_BYTES + 1)
        except (OSError, EOFError) as exc:
            raise HTTPException(status_code=400, detail="invalid gzip body") from exc
        if len(body) > _MAX_BODY_BYTES:
            raise HTTPException(status_code=413, detail="decompressed body too large")
    try:
        events = json.loads(body)
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=400, detail=f"invalid JSON: {exc.msg}") from exc
    if not isinstance(events, list):
        raise HTTPException(status_code=400, detail="body must be a JSON array of log events")
    if len(events) > max_batch:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"batch exceeds {max_batch} events",
        )
    return events


@app.post("/v1/logs", response_model=IngestResponse, status_code=status.HTTP_202_ACCEPTED)
async def ingest_logs(
    request: Request,
    settings: Annotated[Settings, Depends(get_settings)],
    api_key: Annotated[str | None, Depends(verify_api_key)],
) -> IngestResponse:
    """Accept a batch of log events (JSON array, optionally gzip-compressed).

    Responses: 202 with per-item accept/reject counts; 401 bad key; 413 oversize;
    429 rate limited; 503 Kafka unavailable.
    """
    if _producer is None:  # pragma: no cover - lifespan guarantees otherwise
        raise HTTPException(status_code=503, detail="producer not ready")

    events = await _read_batch(request, settings.gateway_max_batch)

    limiter = _get_limiter(settings)
    if limiter is not None:
        caller = api_key or (request.client.host if request.client else "unknown")
        if not limiter.try_consume(caller, len(events)):
            raise HTTPException(status_code=429, detail="rate limit exceeded, slow down")

    valid: list[LogEvent] = []
    errors: list[str] = []
    for i, raw in enumerate(events):
        if not isinstance(raw, dict):
            if len(errors) < 10:
                errors.append(f"event[{i}]: not an object")
            continue
        try:
            valid.append(LogEvent.model_validate(coerce_log_record(raw)))
        except ValidationError as exc:
            if len(errors) < 10:
                errors.append(f"event[{i}]: {exc.errors()[0].get('msg', 'invalid')}")

    if valid:
        try:
            await _producer.send_batch(valid)
        except KafkaError as exc:
            logger.error("kafka produce failed: %s", exc)
            raise HTTPException(status_code=503, detail="log pipeline unavailable") from exc

    rejected = len(events) - len(valid)
    if rejected:
        logger.warning("rejected malformed events", extra={"rejected": rejected})
    return IngestResponse(accepted=len(valid), rejected=rejected, errors=errors)


@app.get("/healthz")
async def healthz() -> dict[str, str]:
    """Liveness — process is up."""
    return {"status": "ok"}


@app.get("/readyz")
async def readyz() -> dict[str, str]:
    """Readiness — can we actually reach Kafka?"""
    if _producer is not None and await _producer.ready():
        return {"status": "ready"}
    raise HTTPException(status_code=503, detail="kafka unreachable")
