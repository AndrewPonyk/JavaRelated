"""Normalizes drug identifiers/names to RxNorm RxCUIs + active ingredients.

Results are memoized in a process-local TTL cache (RxNorm data changes slowly).
"""

from app.core.cache import TTLCache
from app.core.config import get_settings
from app.core.logging import get_logger
from app.integrations.rxnorm_client import RxNormClient
from app.models.drug import Drug, Ingredient

logger = get_logger(__name__)


class RxNormService:
    def __init__(self, client: RxNormClient | None = None) -> None:
        settings = get_settings()
        self._client = client or RxNormClient()
        self._cache: TTLCache[Drug] = TTLCache(
            ttl_seconds=settings.rxnorm_cache_ttl_seconds,
            maxsize=settings.rxnorm_cache_maxsize,
        )

    async def get_drug(self, rxcui: str) -> Drug | None:
        cache_key = f"rxcui:{rxcui}"
        cached = self._cache.get(cache_key)
        if cached is not None:
            return cached

        props = await self._client.get_drug_properties(rxcui)
        if props is None:
            return None
        ingredients = await self._client.get_ingredients(rxcui)
        drug = Drug(
            rxcui=rxcui,
            name=props.get("name", ""),
            tty=props.get("tty"),
            ingredients=[Ingredient(rxcui=i["rxcui"], name=i["name"]) for i in ingredients],
        )
        self._cache.set(cache_key, drug)
        return drug

    async def normalize_name(self, name: str) -> Drug | None:
        cache_key = f"name:{name.strip().lower()}"
        cached = self._cache.get(cache_key)
        if cached is not None:
            return cached

        rxcui = await self._client.find_rxcui_by_name(name)
        if rxcui is None:
            logger.info("rxnorm_no_match", name=name)
            return None
        drug = await self.get_drug(rxcui)
        if drug is not None:
            self._cache.set(cache_key, drug)
        return drug

    async def resolve(
        self, *, rxcui: str | None = None, ndc: str | None = None, name: str | None = None
    ) -> Drug | None:
        """Resolve a flexible drug reference to a normalized Drug."""
        if rxcui:
            return await self.get_drug(rxcui)
        if ndc:
            resolved = await self._client.find_rxcui_by_ndc(ndc)
            return await self.get_drug(resolved) if resolved else None
        if name:
            return await self.normalize_name(name)
        return None
