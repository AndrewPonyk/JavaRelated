"""Product -> ES document mapping (the strict-mappings contract)."""

import uuid
from datetime import UTC, datetime
from decimal import Decimal
from unittest.mock import AsyncMock, MagicMock

import pytest

from app.core.config import Settings
from app.db.models.category import Category
from app.db.models.product import Product
from app.services.indexing_service import IndexingService


def make_product(**overrides) -> Product:  # type: ignore[no-untyped-def]
    defaults = dict(
        id=uuid.uuid4(),
        sku="SKU-1",
        name="Sony Bravia 55",
        description="A television.",
        brand="sony",
        category_id=None,
        price=Decimal("999.99"),
        attributes={"color": "black"},
        popularity=42.0,
        in_stock=True,
        is_active=True,
        created_at=datetime(2026, 1, 1, tzinfo=UTC),
    )
    defaults.update(overrides)
    product = Product(**defaults)  # type: ignore[arg-type]
    return product


def test_to_document_matches_strict_mapping_fields() -> None:
    category = Category(id=uuid.uuid4(), name="TVs", slug="tvs", path="electronics/video/tvs")
    product = make_product()
    product.category = category

    doc = IndexingService.to_document(product)

    assert set(doc) == {
        "id", "sku", "name", "description", "brand", "category_slug", "category_path",
        "price", "attributes", "popularity", "in_stock", "is_active", "created_at", "suggest",
    }  # fmt: skip
    assert doc["category_slug"] == "tvs"
    assert doc["price"] == pytest.approx(999.99)
    assert doc["suggest"]["input"] == ["Sony Bravia 55", "sony Sony Bravia 55"]
    assert doc["suggest"]["weight"] == 42


def test_popularity_clamped_positive_for_rank_feature() -> None:
    doc = IndexingService.to_document(make_product(popularity=0.0))
    assert doc["popularity"] == 1.0
    assert doc["suggest"]["weight"] == 1


async def test_delete_ignores_missing_documents() -> None:
    es = AsyncMock()
    es.options = MagicMock(return_value=es)  # sync method chaining back to the client
    service = IndexingService(es=es, settings=Settings())

    await service.delete_product(uuid.uuid4())

    es.options.assert_called_once_with(ignore_status=404)
    es.delete.assert_awaited_once()
