"""Thin async client over the public RxNav / RxNorm REST API.

Docs: https://lhncbc.nlm.nih.gov/RxNav/APIs/RxNormAPIs.html
"""

import httpx

from app.core.config import get_settings
from app.core.exceptions import RxNormUnavailableError
from app.core.logging import get_logger

logger = get_logger(__name__)


class RxNormClient:
    def __init__(self, base_url: str | None = None) -> None:
        settings = get_settings()
        self._base_url = (base_url or settings.rxnorm_base_url).rstrip("/")
        self._timeout = settings.rxnorm_timeout_seconds

    async def _get(self, path: str, params: dict | None = None) -> dict:
        url = f"{self._base_url}{path}"
        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                resp = await client.get(url, params=params)
                resp.raise_for_status()
                return resp.json()
        except httpx.HTTPError as exc:
            logger.error("rxnorm_request_failed", url=url, error=str(exc))
            raise RxNormUnavailableError("RxNorm service is unavailable") from exc

    async def find_rxcui_by_name(self, name: str) -> str | None:
        # search=2 enables normalized/approximate matching.
        data = await self._get("/rxcui.json", params={"name": name, "search": 2})
        ids = data.get("idGroup", {}).get("rxnormId", [])
        return ids[0] if ids else None

    async def find_rxcui_by_ndc(self, ndc: str) -> str | None:
        data = await self._get("/ndcstatus.json", params={"ndc": ndc})
        return data.get("ndcStatus", {}).get("rxcui") or None

    async def get_drug_properties(self, rxcui: str) -> dict | None:
        data = await self._get(f"/rxcui/{rxcui}/properties.json")
        return data.get("properties")

    async def get_ingredients(self, rxcui: str) -> list[dict]:
        """Active ingredients (tty=IN) related to the given concept."""
        data = await self._get(f"/rxcui/{rxcui}/related.json", params={"tty": "IN"})
        groups = data.get("relatedGroup", {}).get("conceptGroup", [])
        ingredients: list[dict] = []
        for group in groups:
            for prop in group.get("conceptProperties", []) or []:
                ingredients.append({"rxcui": prop["rxcui"], "name": prop["name"]})
        return ingredients
