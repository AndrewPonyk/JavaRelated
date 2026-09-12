"""Search use-case: build query → execute against ES → map to API DTOs.

Degradation policy (ARCHITECTURE §2.4): if a query carrying an LTR rescore fails,
retry once without the rescore (plain BM25) before failing the request — a missing
or broken ranking model must degrade ordering quality, not availability.
"""

from typing import Any

import structlog
from elasticsearch import ApiError, TransportError

from app.core.config import Settings
from app.core.exceptions import SearchBackendUnavailableError
from app.schemas.common import PageMeta
from app.schemas.search import ProductHit, SearchResponse
from app.search.facets import parse_facets
from app.search.query_builder import SearchQuery, build_search_body
from app.services.event_service import EventService, fire_and_forget

logger = structlog.get_logger(__name__)


class SearchService:
    def __init__(self, es: Any, settings: Settings, events: EventService | None = None) -> None:
        self._es = es
        self._settings = settings
        self._events = events

    async def search(self, request: SearchQuery, session_id: str = "anonymous") -> SearchResponse:
        body = build_search_body(
            request,
            ltr_mode=self._settings.ltr_mode,
            ltr_model_name=self._settings.ltr_model_name,
            ltr_rescore_window=self._settings.ltr_rescore_window,
        )
        response = await self._execute(body)

        total = response["hits"]["total"]["value"]
        hits = [self._to_hit(raw) for raw in response["hits"]["hits"]]
        facets = parse_facets(response.get("aggregations", {}), selected=request.filters)

        logger.info(
            "search_executed",
            q=request.query,
            total=total,
            took_ms=response.get("took", -1),
            ltr=self._settings.ltr_mode,
        )

        if self._events is not None:
            fire_and_forget(
                self._events.log_search(
                    query=request.query,
                    filters=request.filters,
                    results_count=total,
                    shown_product_ids=[str(hit.id) for hit in hits],
                    session_id=session_id,
                )
            )

        return SearchResponse(
            query=request.query,
            hits=hits,
            facets=facets,
            meta=PageMeta.build(page=request.page, size=request.size, total=total),
            took_ms=response.get("took", 0),
        )

    async def _execute(self, body: dict[str, Any]) -> Any:
        try:
            return await self._es.search(index=self._settings.es_products_alias, body=body)
        except ApiError as exc:
            if "rescore" in body:  # LTR fallback: serve BM25 instead of failing
                logger.warning("ltr_rescore_failed_falling_back", error=str(exc))
                fallback = {k: v for k, v in body.items() if k != "rescore"}
                try:
                    return await self._es.search(
                        index=self._settings.es_products_alias, body=fallback
                    )
                except (ApiError, TransportError) as retry_exc:
                    logger.error("es_search_failed", error=str(retry_exc))
                    raise SearchBackendUnavailableError() from retry_exc
            logger.error("es_search_failed", error=str(exc))
            raise SearchBackendUnavailableError() from exc
        except TransportError as exc:
            logger.error("es_search_failed", error=str(exc))
            raise SearchBackendUnavailableError() from exc

    @staticmethod
    def _to_hit(raw: dict[str, Any]) -> ProductHit:
        source = raw["_source"]
        return ProductHit(
            id=source["id"],
            sku=source["sku"],
            name=source["name"],
            brand=source.get("brand"),
            price=source.get("price", 0.0),
            category_slug=source.get("category_slug"),
            in_stock=source.get("in_stock", True),
            score=raw.get("_score"),
        )
