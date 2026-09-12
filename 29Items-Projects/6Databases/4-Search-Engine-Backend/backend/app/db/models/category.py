"""Category tree (adjacency list + materialized path for cheap facet display)."""

import uuid

from sqlalchemy import ForeignKey, String
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base


class Category(Base):
    __tablename__ = "categories"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name: Mapped[str] = mapped_column(String(128))
    slug: Mapped[str] = mapped_column(String(128), unique=True, index=True)
    parent_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("categories.id")
    )
    # Materialized path, e.g. "electronics/audio/headphones" — denormalized into ES.
    path: Mapped[str] = mapped_column(String(512), default="")

    parent: Mapped["Category | None"] = relationship(remote_side=[id])
    products: Mapped[list["Product"]] = relationship(back_populates="category")


from app.db.models.product import Product  # noqa: E402  (resolve forward ref)
