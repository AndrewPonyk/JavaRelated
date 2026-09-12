"""SQLAlchemy data access for products.

Every mutation enqueues an index_outbox row IN THE SAME TRANSACTION as the catalog
change (transactional outbox): either both commit or neither does, which is what
keeps Elasticsearch crash-consistent with PostgreSQL. No business rules here.
"""

from uuid import UUID

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models.index_outbox import IndexOutbox, OutboxOp
from app.db.models.product import Product
from app.schemas.product import ProductCreate, ProductUpdate


class ProductRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def create(self, data: ProductCreate) -> Product:
        product = Product(**data.model_dump())
        self._session.add(product)
        await self._session.flush()  # assigns the id used by the outbox row
        self._enqueue_index_op(product.id, "upsert")
        await self._session.commit()
        await self._session.refresh(product)
        return product

    async def get(self, product_id: UUID) -> Product | None:
        return await self._session.get(Product, product_id)

    async def list(self, *, page: int, size: int) -> tuple[list[Product], int]:
        total = await self._session.scalar(
            select(func.count()).select_from(Product).where(Product.is_active.is_(True))
        )
        result = await self._session.scalars(
            select(Product)
            .where(Product.is_active.is_(True))
            .order_by(Product.created_at.desc())
            .offset((page - 1) * size)
            .limit(size)
        )
        return list(result), int(total or 0)

    async def update(self, product_id: UUID, data: ProductUpdate) -> Product | None:
        product = await self.get(product_id)
        if product is None:
            return None
        for field, value in data.model_dump(exclude_unset=True).items():
            setattr(product, field, value)
        self._enqueue_index_op(product.id, "upsert")
        await self._session.commit()
        await self._session.refresh(product)
        return product

    async def soft_delete(self, product_id: UUID) -> Product | None:
        product = await self.get(product_id)
        if product is None:
            return None
        product.is_active = False
        self._enqueue_index_op(product.id, "delete")
        await self._session.commit()
        return product

    def _enqueue_index_op(self, product_id: UUID, op: OutboxOp) -> None:
        self._session.add(IndexOutbox(product_id=str(product_id), op=op))
