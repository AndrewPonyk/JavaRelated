"""Document ingest + CRUD DTOs."""

from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, Field

from app.core.config import Backend


class DocumentIngestRequest(BaseModel):
    text: str = Field(min_length=1)
    source: str | None = Field(
        default=None, description="Origin identifier, e.g. a URL or file id."
    )
    metadata: dict[str, Any] = Field(default_factory=dict)
    backend: Backend | None = Field(
        default=None, description="Target backend; defaults to configured."
    )


class DocumentUpdateRequest(BaseModel):
    text: str = Field(min_length=1)
    metadata: dict[str, Any] = Field(default_factory=dict)
    backend: Backend | None = None


class DocumentIngestResponse(BaseModel):
    document_id: str
    chunks_indexed: int
    backend: str


class ChunkSummary(BaseModel):
    id: str
    chunk_index: int
    text: str


class DocumentDetail(BaseModel):
    id: str
    source: str | None = None
    text: str
    metadata: dict[str, Any] = Field(default_factory=dict)
    created_at: datetime | None = None
    num_chunks: int = 0


class DocumentSummary(BaseModel):
    id: str
    source: str | None = None
    created_at: datetime | None = None
    num_chunks: int = 0


class DocumentListResponse(BaseModel):
    total: int
    limit: int
    offset: int
    items: list[DocumentSummary]
