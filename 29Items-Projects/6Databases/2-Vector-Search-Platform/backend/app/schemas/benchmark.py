"""Benchmark DTOs."""

from __future__ import annotations

from pydantic import BaseModel, Field

from app.core.config import Backend
from app.schemas.search import SearchMode


class QueryCase(BaseModel):
    """One labeled query: the text + the set of ground-truth relevant document ids."""

    query: str = Field(min_length=1)
    relevant_ids: list[str] = Field(default_factory=list)


class BenchmarkRequest(BaseModel):
    backends: list[Backend] = Field(min_length=1)
    cases: list[QueryCase] = Field(min_length=1)
    k: int = Field(default=10, ge=1, le=100)
    mode: SearchMode = SearchMode.vector


class BenchmarkResult(BaseModel):
    backend: str
    k: int
    num_queries: int
    recall_at_k: float
    mrr: float
    latency_p50_ms: float
    latency_p95_ms: float
    qps: float


class BenchmarkResponse(BaseModel):
    k: int
    results: list[BenchmarkResult]
