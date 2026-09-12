"""Catalog CRUD API contract, backed by an in-memory repository fake.

The fake implements the ProductRepository interface (create/get/list/update/
soft_delete + transactional outbox semantics) so the service, validation, auth,
and serialization layers are exercised for real. PG-backed behavior is covered
by test_full_stack.py.
"""

import uuid
from collections.abc import AsyncIterator
from datetime import UTC, datetime
from types import SimpleNamespace

import pytest
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient

from app.api import deps
from app.schemas.product import ProductCreate, ProductUpdate
from app.services.product_service import ProductService

ADMIN = {"X-API-Key": "change-me"}

PAYLOAD = {
    "sku": "SONY-TV-55",
    "name": "Sony Bravia 55",
    "description": "A very nice TV",
    "brand": "sony",
    "price": "999.99",
    "attributes": {"color": "black"},
    "in_stock": True,
}


class FakeProductRepository:
    def __init__(self) -> None:
        self._items: dict[uuid.UUID, SimpleNamespace] = {}
        self.outbox: list[tuple[uuid.UUID, str]] = []

    async def create(self, data: ProductCreate) -> SimpleNamespace:
        now = datetime.now(UTC)
        product = SimpleNamespace(
            id=uuid.uuid4(),
            popularity=1.0,
            is_active=True,
            created_at=now,
            updated_at=now,
            **data.model_dump(),
        )
        self._items[product.id] = product
        self.outbox.append((product.id, "upsert"))
        return product

    async def get(self, product_id: uuid.UUID) -> SimpleNamespace | None:
        return self._items.get(product_id)

    async def list(self, *, page: int, size: int) -> tuple[list[SimpleNamespace], int]:
        active = [p for p in self._items.values() if p.is_active]
        return active[(page - 1) * size : page * size], len(active)

    async def update(self, product_id: uuid.UUID, data: ProductUpdate) -> SimpleNamespace | None:
        product = self._items.get(product_id)
        if product is None:
            return None
        for field, value in data.model_dump(exclude_unset=True).items():
            setattr(product, field, value)
        self.outbox.append((product_id, "upsert"))
        return product

    async def soft_delete(self, product_id: uuid.UUID) -> SimpleNamespace | None:
        product = self._items.get(product_id)
        if product is None:
            return None
        product.is_active = False
        self.outbox.append((product_id, "delete"))
        return product


@pytest.fixture
def fake_repo() -> FakeProductRepository:
    return FakeProductRepository()


@pytest.fixture
async def catalog_client(
    test_app: FastAPI, fake_repo: FakeProductRepository
) -> AsyncIterator[AsyncClient]:
    test_app.dependency_overrides[deps.get_product_service] = lambda: ProductService(
        repository=fake_repo  # type: ignore[arg-type]
    )
    async with AsyncClient(transport=ASGITransport(app=test_app), base_url="http://test") as c:
        yield c


async def test_create_requires_admin_key(catalog_client: AsyncClient) -> None:
    response = await catalog_client.post("/api/v1/products", json=PAYLOAD)
    assert response.status_code == 401


async def test_create_validates_payload(catalog_client: AsyncClient) -> None:
    bad = {**PAYLOAD, "price": "-5", "sku": ""}
    response = await catalog_client.post("/api/v1/products", json=bad, headers=ADMIN)
    assert response.status_code == 422


async def test_full_crud_lifecycle_with_outbox(
    catalog_client: AsyncClient, fake_repo: FakeProductRepository
) -> None:
    # Create
    created = await catalog_client.post("/api/v1/products", json=PAYLOAD, headers=ADMIN)
    assert created.status_code == 201
    body = created.json()
    product_id = body["id"]
    assert body["sku"] == "SONY-TV-55"
    assert body["is_active"] is True

    # Read
    fetched = await catalog_client.get(f"/api/v1/products/{product_id}")
    assert fetched.status_code == 200
    assert fetched.json()["name"] == "Sony Bravia 55"

    # List
    listed = await catalog_client.get("/api/v1/products")
    assert listed.status_code == 200
    assert listed.json()["meta"]["total"] == 1

    # Update
    updated = await catalog_client.put(
        f"/api/v1/products/{product_id}", json={"price": "899.00"}, headers=ADMIN
    )
    assert updated.status_code == 200
    assert updated.json()["price"] == "899.00" or float(updated.json()["price"]) == 899.0

    # Delete (soft) — disappears from listings
    deleted = await catalog_client.delete(f"/api/v1/products/{product_id}", headers=ADMIN)
    assert deleted.status_code == 204
    assert (await catalog_client.get("/api/v1/products")).json()["meta"]["total"] == 0

    # Every mutation produced an index-sync intent, delete last.
    ops = [op for _, op in fake_repo.outbox]
    assert ops == ["upsert", "upsert", "delete"]


async def test_get_unknown_product_is_404_problem(catalog_client: AsyncClient) -> None:
    response = await catalog_client.get(f"/api/v1/products/{uuid.uuid4()}")
    assert response.status_code == 404
    assert response.json()["code"] == "not_found"


async def test_update_unknown_product_is_404(catalog_client: AsyncClient) -> None:
    response = await catalog_client.put(
        f"/api/v1/products/{uuid.uuid4()}", json={"name": "X"}, headers=ADMIN
    )
    assert response.status_code == 404


async def test_update_rejects_explicit_null_for_required_fields(
    catalog_client: AsyncClient,
) -> None:
    created = await catalog_client.post("/api/v1/products", json=PAYLOAD, headers=ADMIN)
    product_id = created.json()["id"]

    # NOT NULL columns: explicit null must be a 422, not a 500 from the constraint.
    response = await catalog_client.put(
        f"/api/v1/products/{product_id}", json={"name": None}, headers=ADMIN
    )
    assert response.status_code == 422

    # Nullable columns may still be cleared explicitly.
    response = await catalog_client.put(
        f"/api/v1/products/{product_id}", json={"brand": None}, headers=ADMIN
    )
    assert response.status_code == 200
    assert response.json()["brand"] is None
