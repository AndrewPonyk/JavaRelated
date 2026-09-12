"""Drug lookup/search/CRUD: graph-first, RxNorm fallback."""

from app.core.exceptions import DrugNotFoundError
from app.db.repositories.drug_repository import DrugRepository
from app.models.drug import (
    Drug,
    DrugClassUpsert,
    DrugListResponse,
    DrugSearchResult,
    DrugUpsert,
    Ingredient,
)
from app.services.rxnorm_service import RxNormService


def _row_to_drug(row: dict) -> Drug:
    return Drug(
        rxcui=row["rxcui"],
        name=row["name"],
        tty=row.get("tty"),
        ingredients=[
            Ingredient(rxcui=i["rxcui"], name=i["name"])
            for i in row.get("ingredients", [])
            if i.get("rxcui")
        ],
        drug_classes=[c for c in row.get("drug_classes", []) if c],
    )


class DrugService:
    def __init__(self, repo: DrugRepository, rxnorm: RxNormService) -> None:
        self._repo = repo
        self._rxnorm = rxnorm

    async def get(self, rxcui: str) -> Drug:
        row = await self._repo.get_drug(rxcui)
        if row:
            return _row_to_drug(row)
        # Fall back to RxNorm if the drug is not yet in our graph.
        drug = await self._rxnorm.get_drug(rxcui)
        if drug is None:
            raise DrugNotFoundError(f"No drug found for rxcui={rxcui}")
        return drug

    async def search(self, query: str) -> DrugSearchResult:
        # Graph-first (fast, local), then RxNorm normalization as a fallback.
        local = await self._repo.search_by_name(query)
        if local:
            return DrugSearchResult(
                query=query,
                matches=[Drug(rxcui=r["rxcui"], name=r["name"], tty=r.get("tty")) for r in local],
            )
        drug = await self._rxnorm.normalize_name(query)
        return DrugSearchResult(query=query, matches=[drug] if drug else [])

    # ---- admin / CRUD ------------------------------------------------------
    async def create(self, payload: DrugUpsert) -> Drug:
        await self._repo.upsert_drug_full(payload)
        return await self.get(payload.rxcui)

    async def upsert_class(self, payload: DrugClassUpsert) -> None:
        await self._repo.upsert_class(payload.class_id, payload.name, payload.class_type)

    async def list(self, limit: int = 50, offset: int = 0) -> DrugListResponse:
        rows, total = await self._repo.list_drugs(limit=limit, offset=offset)
        return DrugListResponse(
            items=[Drug(rxcui=r["rxcui"], name=r["name"], tty=r.get("tty")) for r in rows],
            total=total,
            limit=limit,
            offset=offset,
        )

    async def delete(self, rxcui: str) -> bool:
        return await self._repo.delete_drug(rxcui)
