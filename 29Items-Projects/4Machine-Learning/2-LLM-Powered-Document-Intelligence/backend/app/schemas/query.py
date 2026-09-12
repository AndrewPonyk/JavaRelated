"""Pydantic DTOs for the query (RAG Q&A) API."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class Citation(BaseModel):
    document_id: str
    chunk_index: int
    score: float


class QueryRequest(BaseModel):
    question: str = Field(..., min_length=1, max_length=4000)
    # Optional retrieval filter: restrict to a corpus type.
    doc_type: Literal["legal", "medical", "general"] | None = None
    top_k: int | None = Field(default=None, ge=1, le=50)
    stream: bool = False


class QueryResponse(BaseModel):
    query_id: str
    answer: str
    citations: list[Citation]
    model_id: str
    latency_ms: int


class FeedbackRequest(BaseModel):
    # -1 (unhelpful) | 0 (neutral) | +1 (helpful)
    value: int = Field(..., ge=-1, le=1)


class SummaryResponse(BaseModel):
    document_id: str
    summary: str
