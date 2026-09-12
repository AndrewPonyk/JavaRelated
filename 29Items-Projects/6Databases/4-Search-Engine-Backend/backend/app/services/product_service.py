"""Catalog use-cases. PostgreSQL is the source of truth; index propagation happens
via the transactional outbox written by the repository and drained by the outbox
worker (services/outbox_worker.py) — this service never talks to Elasticsearch."""

from uuid import UUID

from app.core.exceptions import NotFoundError
from app.repositories.product_repository import ProductRepository
from app.schemas.common import PageMeta
from app.schemas.product import ProductCreate, ProductListResponse, ProductRead, ProductUpdate


class ProductService:
    def __init__(self, repository: ProductRepository) -> None:
        self._repository = repository

    async def create(self, data: ProductCreate) -> ProductRead:
        product = await self._repository.create(data)
        return ProductRead.model_validate(product)

    async def get(self, product_id: UUID) -> ProductRead:
        product = await self._repository.get(product_id)
        if product is None:
            raise NotFoundError(f"Product {product_id} not found")
        return ProductRead.model_validate(product)

    async def list(self, page: int, size: int) -> ProductListResponse:
        products, total = await self._repository.list(page=page, size=size)
        return ProductListResponse(
            items=[ProductRead.model_validate(p) for p in products],
            meta=PageMeta.build(page=page, size=size, total=total),
        )

    async def update(self, product_id: UUID, data: ProductUpdate) -> ProductRead:
        product = await self._repository.update(product_id, data)
        if product is None:
            raise NotFoundError(f"Product {product_id} not found")
        return ProductRead.model_validate(product)

    async def delete(self, product_id: UUID) -> None:
        """Soft delete: is_active=False in PG; the outbox row removes the ES document."""
        product = await self._repository.soft_delete(product_id)
        if product is None:
            raise NotFoundError(f"Product {product_id} not found")
