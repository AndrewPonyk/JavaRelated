"""Shared API schemas: pagination envelope and query params."""
from __future__ import annotations

from typing import Annotated, Generic, TypeVar

from fastapi import Query
from pydantic import BaseModel, Field

T = TypeVar("T")


class Page(BaseModel, Generic[T]):
    """A bounded slice of a collection with the total available count."""

    items: list[T]
    total: int = Field(ge=0)
    limit: int = Field(ge=1)
    offset: int = Field(ge=0)


# Reusable, validated pagination query params (bounds enforced by FastAPI).
LimitParam = Annotated[int, Query(ge=1, le=200, description="Max items to return.")]
OffsetParam = Annotated[int, Query(ge=0, description="Items to skip.")]
