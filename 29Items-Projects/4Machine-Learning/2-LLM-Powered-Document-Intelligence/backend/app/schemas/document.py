"""Pydantic DTOs for the documents API (wire contract, decoupled from ORM)."""

from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict


class DocumentOut(BaseModel):
    """Document as returned to clients."""

    model_config = ConfigDict(from_attributes=True)

    id: str
    filename: str
    doc_type: str
    status: str
    chunk_count: int
    created_at: datetime


class DocumentUploadResponse(BaseModel):
    """Returned after upload. Status is ``pending`` (queue mode) or ``indexed`` (inline)."""

    document_id: str
    status: Literal["pending", "indexed", "failed"] = "pending"


class DocumentList(BaseModel):
    items: list[DocumentOut]
    total: int
    limit: int
    offset: int
