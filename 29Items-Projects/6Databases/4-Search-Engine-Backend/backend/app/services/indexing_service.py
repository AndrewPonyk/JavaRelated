"""PG → ES document mapping and index write operations.

The document shape must match elasticsearch/indexes/products.index.json
(mappings are `dynamic: strict` — unknown fields are rejected on purpose).
Consumed by the outbox worker (live sync) and the reindex/seed scripts (bulk).
"""

from collections.abc import Iterable
from typing import Any
from uuid import UUID

import structlog
from elasticsearch.helpers import async_bulk

from app.core.config import Settings
from app.db.models.product import Product

logger = structlog.get_logger(__name__)


class IndexingService:
    def __init__(self, es: Any, settings: Settings) -> None:
        self._es = es
        self._alias = settings.es_products_alias

    @staticmethod
    def to_document(product: Product) -> dict[str, Any]:
        suggest_inputs = [product.name]
        if product.brand:
            suggest_inputs.append(f"{product.brand} {product.name}")
        return {
            "id": str(product.id),
            "sku": product.sku,
            "name": product.name,
            "description": product.description,
            "brand": product.brand,
            "category_slug": product.category.slug if product.category else None,
            "category_path": product.category.path if product.category else None,
            "price": float(product.price),
            "attributes": product.attributes or {},
            # rank_feature values must be strictly positive (TECH-NOTES §3.6 #8).
            "popularity": max(product.popularity, 1.0),
            "in_stock": product.in_stock,
            "is_active": product.is_active,
            "created_at": product.created_at.isoformat() if product.created_at else None,
            "suggest": {
                "input": suggest_inputs,
                "weight": int(max(product.popularity, 1.0)),
            },
        }

    async def index_product(self, product: Product, index: str | None = None) -> None:
        await self._es.index(
            index=index or self._alias,
            id=str(product.id),
            document=self.to_document(product),
        )

    async def delete_product(self, product_id: UUID, index: str | None = None) -> None:
        await self._es.options(ignore_status=404).delete(
            index=index or self._alias, id=str(product_id)
        )

    async def bulk_index(
        self, products: Iterable[Product], index: str | None = None
    ) -> tuple[int, int]:
        """Bulk-index products; returns (indexed, failed). Failures are logged per doc
        rather than raised so one bad document cannot abort a reindex."""
        target = index or self._alias

        def actions() -> Iterable[dict[str, Any]]:
            for product in products:
                yield {
                    "_index": target,
                    "_id": str(product.id),
                    "_source": self.to_document(product),
                }

        indexed, errors = await async_bulk(
            self._es, actions(), raise_on_error=False, stats_only=False
        )
        for error in errors:  # type: ignore[union-attr]
            logger.error("bulk_index_doc_failed", error=str(error))
        failed = len(errors)  # type: ignore[arg-type]
        logger.info("bulk_indexed", indexed=indexed, failed=failed, index=target)
        return indexed, failed
