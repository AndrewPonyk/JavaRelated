"""Category taxonomy CRUD.

A small, slowly-changing dataset backed by Postgres (SQLite in tests). Demonstrates
the service/repository pattern with Pydantic validation and DI'd DB sessions. Reads
require the ``taxonomy:read`` scope (or an API key); writes require ``taxonomy:write``.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.security import authorize
from app.db.session import get_db
from app.models.db_models import Category as CategoryORM
from app.models.schemas import Category, CategoryCreate, CategoryUpdate

router = APIRouter(prefix="/categories", tags=["categories"])


def _resolve_parent_id(db: Session, parent_name: str | None) -> int | None:
    if not parent_name:
        return None
    parent = db.scalar(select(CategoryORM).where(CategoryORM.name == parent_name))
    if parent is None:
        raise HTTPException(status_code=404, detail="Parent category not found")
    return parent.id


@router.get("", response_model=list[Category], dependencies=[Depends(authorize("taxonomy:read"))])
def list_categories(
    db: Session = Depends(get_db),
    limit: int = Query(default=100, le=500, ge=1),
    offset: int = Query(default=0, ge=0),
) -> list[CategoryORM]:
    stmt = select(CategoryORM).order_by(CategoryORM.id).limit(limit).offset(offset)
    return list(db.scalars(stmt).all())


@router.post(
    "",
    response_model=Category,
    status_code=status.HTTP_201_CREATED,
    dependencies=[Depends(authorize("taxonomy:write"))],
)
def create_category(payload: CategoryCreate, db: Session = Depends(get_db)) -> CategoryORM:
    parent_id = _resolve_parent_id(db, payload.parent)
    category = CategoryORM(name=payload.name, parent_id=parent_id, description=payload.description)
    db.add(category)
    try:
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=f"Category '{payload.name}' already exists",
        ) from exc
    db.refresh(category)
    return category


@router.get(
    "/{category_id}",
    response_model=Category,
    dependencies=[Depends(authorize("taxonomy:read"))],
)
def get_category(category_id: int, db: Session = Depends(get_db)) -> CategoryORM:
    category = db.get(CategoryORM, category_id)
    if category is None:
        raise HTTPException(status_code=404, detail="Category not found")
    return category


@router.put(
    "/{category_id}",
    response_model=Category,
    dependencies=[Depends(authorize("taxonomy:write"))],
)
def update_category(
    category_id: int, payload: CategoryUpdate, db: Session = Depends(get_db)
) -> CategoryORM:
    category = db.get(CategoryORM, category_id)
    if category is None:
        raise HTTPException(status_code=404, detail="Category not found")

    if payload.name is not None:
        category.name = payload.name
    if payload.description is not None:
        category.description = payload.description
    if payload.parent is not None:
        category.parent_id = _resolve_parent_id(db, payload.parent)

    try:
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Name conflict") from exc
    db.refresh(category)
    return category


@router.delete(
    "/{category_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    response_model=None,
    dependencies=[Depends(authorize("taxonomy:write"))],
)
def delete_category(category_id: int, db: Session = Depends(get_db)) -> None:
    category = db.get(CategoryORM, category_id)
    if category is None:
        raise HTTPException(status_code=404, detail="Category not found")
    db.delete(category)
    db.commit()
