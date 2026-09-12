"""Cross-cutting API schemas: problem details, pagination, auth claims."""

from __future__ import annotations

from typing import Generic, Literal, TypeVar
from uuid import UUID

from pydantic import BaseModel

T = TypeVar("T")

Role = Literal["student", "instructor", "admin"]


class Problem(BaseModel):
    """RFC 7807 problem details — the shape of every non-2xx response."""

    type: str
    title: str
    status: int
    detail: str
    request_id: str


class UserClaims(BaseModel):
    """Verified JWT claims injected by the auth dependency."""

    user_id: UUID
    role: Role = "student"


class Page(BaseModel, Generic[T]):
    items: list[T]
    total: int
    limit: int
    offset: int
