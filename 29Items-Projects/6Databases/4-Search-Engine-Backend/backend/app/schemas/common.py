"""Shared DTOs used across endpoints."""

import math

from pydantic import BaseModel


class PageMeta(BaseModel):
    page: int
    size: int
    total: int
    pages: int

    @classmethod
    def build(cls, *, page: int, size: int, total: int) -> "PageMeta":
        return cls(page=page, size=size, total=total, pages=math.ceil(total / size) if size else 0)


class ProblemDetail(BaseModel):
    """RFC-7807 error body — documented shape of every non-2xx response."""

    type: str
    title: str
    status: int
    detail: str
    code: str
    request_id: str
