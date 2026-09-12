"""Catalog CRUD. Writes commit to PostgreSQL first, then propagate to the search index.
Write operations are admin-key protected in the scaffold (JWT roles in Phase 2)."""

from uuid import UUID

from fastapi import APIRouter, Depends, Query, status

from app.api.deps import get_product_service, require_admin_key
from app.schemas.product import ProductCreate, ProductListResponse, ProductRead, ProductUpdate
from app.services.product_service import ProductService

router = APIRouter()


@router.get("", response_model=ProductListResponse, summary="List products")
async def list_products(
    page: int = Query(default=1, ge=1),
    size: int = Query(default=20, ge=1, le=100),
    service: ProductService = Depends(get_product_service),
) -> ProductListResponse:
    return await service.list(page=page, size=size)


@router.get("/{product_id}", response_model=ProductRead, summary="Get a product")
async def get_product(
    product_id: UUID,
    service: ProductService = Depends(get_product_service),
) -> ProductRead:
    return await service.get(product_id)


@router.post(
    "",
    response_model=ProductRead,
    status_code=status.HTTP_201_CREATED,
    dependencies=[Depends(require_admin_key)],
    summary="Create a product",
)
async def create_product(
    payload: ProductCreate,
    service: ProductService = Depends(get_product_service),
) -> ProductRead:
    return await service.create(payload)


@router.put(
    "/{product_id}",
    response_model=ProductRead,
    dependencies=[Depends(require_admin_key)],
    summary="Update a product",
)
async def update_product(
    product_id: UUID,
    payload: ProductUpdate,
    service: ProductService = Depends(get_product_service),
) -> ProductRead:
    return await service.update(product_id, payload)


@router.delete(
    "/{product_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    dependencies=[Depends(require_admin_key)],
    summary="Soft-delete a product",
)
async def delete_product(
    product_id: UUID,
    service: ProductService = Depends(get_product_service),
) -> None:
    await service.delete(product_id)
