"""Calculation audit trail (ARCHITECTURE.md §2.3): one row per priced request
makes any historical number re-derivable (params hash + seed + request id).

Runs as a FastAPI background task after the response is sent, so audit I/O
never adds latency to the pricing path.
"""

from __future__ import annotations

import hashlib
import json
import logging

from app.db.models import CalculationAudit
from app.db.session import get_session_factory

log = logging.getLogger("quantfinlib.api.audit")


def params_hash(params: dict) -> str:
    """Canonical, order-independent hash of the request parameters."""
    canonical = json.dumps(params, sort_keys=True, separators=(",", ":"), default=str)
    return hashlib.sha256(canonical.encode()).hexdigest()


async def record_audit(
    *,
    request_id: str,
    caller: str,
    endpoint: str,
    params: dict,
    latency_ms: float,
    seed: int | None = None,
) -> None:
    try:
        async with get_session_factory()() as session:
            session.add(
                CalculationAudit(
                    request_id=request_id,
                    caller=caller,
                    endpoint=endpoint,
                    params_hash=params_hash(params),
                    seed=seed,
                    latency_ms=latency_ms,
                )
            )
            await session.commit()
    except Exception:  # audit must never take down the service
        log.exception("failed to write audit row", extra={"request_id": request_id})
