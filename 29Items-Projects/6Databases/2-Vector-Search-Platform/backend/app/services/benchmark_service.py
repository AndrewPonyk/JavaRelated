"""Benchmarking: recall@k, MRR, and latency across backends.

Given a labeled dataset (queries -> set of relevant document ids), run each query through a
backend and compute retrieval quality + latency. Retrieved chunk ids are mapped back to their
document ids before scoring, so ground truth is expressed at the document level. The metric
functions are pure and unit-tested against golden values.
"""

from __future__ import annotations

import time

import structlog

from app.core.config import Backend
from app.schemas.benchmark import BenchmarkResult, QueryCase
from app.schemas.search import SearchMode
from app.services.search_service import SearchService

log = structlog.get_logger(__name__)


def recall_at_k(retrieved_ids: list[str], relevant_ids: set[str], k: int) -> float:
    """Fraction of relevant items found within the top-k retrieved."""
    if not relevant_ids:
        return 0.0
    top_k = set(retrieved_ids[:k])
    return len(top_k & relevant_ids) / len(relevant_ids)


def mean_reciprocal_rank(retrieved_ids: list[str], relevant_ids: set[str]) -> float:
    """1 / rank of the first relevant hit (0 if none retrieved)."""
    for rank, doc_id in enumerate(retrieved_ids, start=1):
        if doc_id in relevant_ids:
            return 1.0 / rank
    return 0.0


def percentile(values: list[float], p: float) -> float:
    """Nearest-rank percentile (p in [0, 100]) of a list of latencies (ms)."""
    if not values:
        return 0.0
    ordered = sorted(values)
    idx = min(len(ordered) - 1, int(round((p / 100.0) * (len(ordered) - 1))))
    return ordered[idx]


def _dedupe_preserve_order(ids: list[str]) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []
    for i in ids:
        if i not in seen:
            seen.add(i)
            out.append(i)
    return out


class BenchmarkService:
    def __init__(self, search_service: SearchService) -> None:
        self._search = search_service

    async def run(
        self,
        cases: list[QueryCase],
        *,
        backend: Backend | str,
        k: int = 10,
        mode: SearchMode = SearchMode.vector,
    ) -> BenchmarkResult:
        """Execute every query case against ``backend`` and aggregate metrics."""
        recalls: list[float] = []
        rr: list[float] = []
        latencies_ms: list[float] = []

        for case in cases:
            started = time.perf_counter()
            results = await self._search.search(case.query, k=k, backend=backend, mode=mode)
            latencies_ms.append((time.perf_counter() - started) * 1000.0)

            # Map retrieved chunks -> their document ids for document-level recall.
            retrieved_docs = _dedupe_preserve_order(
                [str(r.metadata.get("document_id", r.id)) for r in results]
            )
            relevant = set(case.relevant_ids)
            recalls.append(recall_at_k(retrieved_docs, relevant, k))
            rr.append(mean_reciprocal_rank(retrieved_docs, relevant))

        n = max(len(cases), 1)
        total_ms = sum(latencies_ms)
        return BenchmarkResult(
            backend=str(backend.value if isinstance(backend, Backend) else backend),
            k=k,
            num_queries=len(cases),
            recall_at_k=sum(recalls) / n,
            mrr=sum(rr) / n,
            latency_p50_ms=percentile(latencies_ms, 50),
            latency_p95_ms=percentile(latencies_ms, 95),
            qps=(len(cases) / (total_ms / 1000.0)) if total_ms > 0 else 0.0,
        )
