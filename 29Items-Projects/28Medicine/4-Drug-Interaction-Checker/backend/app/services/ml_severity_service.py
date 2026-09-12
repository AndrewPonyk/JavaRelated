"""Client for the ML severity-prediction microservice.

ML is *advisory*: failures degrade gracefully and never fail the overall
interaction check.
"""

import httpx
from pydantic import BaseModel

from app.core.config import get_settings
from app.core.logging import get_logger
from app.models.common import Severity

logger = get_logger(__name__)

PairKey = tuple[str, str]


class SeverityPrediction(BaseModel):
    severity: Severity
    confidence: float
    mechanism: str | None = None


class MLSeverityService:
    def __init__(self, base_url: str | None = None) -> None:
        settings = get_settings()
        self._base_url = (base_url or settings.ml_service_url).rstrip("/")
        self._enabled = settings.ml_enabled
        self._timeout = settings.ml_timeout_seconds

    async def predict(self, rxcui_a: str, rxcui_b: str) -> SeverityPrediction | None:
        if not self._enabled:
            return None
        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                resp = await client.post(
                    f"{self._base_url}/predict",
                    json={"rxcui_a": rxcui_a, "rxcui_b": rxcui_b},
                )
                resp.raise_for_status()
                return SeverityPrediction(**resp.json())
        except (httpx.HTTPError, ValueError) as exc:
            logger.warning("ml_predict_failed", rxcui_a=rxcui_a, rxcui_b=rxcui_b, error=str(exc))
            return None

    async def predict_batch(self, pairs: list[PairKey]) -> dict[PairKey, SeverityPrediction]:
        """Score many pairs in a single call. Returns {} on any failure (fail-soft)."""
        if not self._enabled or not pairs:
            return {}
        body = {"pairs": [{"rxcui_a": a, "rxcui_b": b} for a, b in pairs]}
        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                resp = await client.post(f"{self._base_url}/predict/batch", json=body)
                resp.raise_for_status()
                payload = resp.json()
        except (httpx.HTTPError, ValueError) as exc:
            logger.warning("ml_predict_batch_failed", count=len(pairs), error=str(exc))
            return {}

        out: dict[PairKey, SeverityPrediction] = {}
        for item in payload.get("predictions", []):
            try:
                key = (item["rxcui_a"], item["rxcui_b"])
                out[key] = SeverityPrediction(
                    severity=item["severity"],
                    confidence=item["confidence"],
                    mechanism=item.get("mechanism"),
                )
            except (KeyError, ValueError):
                continue
        return out
