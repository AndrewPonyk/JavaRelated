"""Interaction-event API contract."""

from uuid import UUID

from pydantic import BaseModel, Field


class ClickEventIn(BaseModel):
    query: str = Field(min_length=1, max_length=200, description="Query the user searched")
    product_id: UUID
    position: int = Field(ge=0, le=500, description="0-based rank of the clicked result")
