"""Benchmark endpoints: run recall@k across one or more backends."""

from __future__ import annotations

from fastapi import APIRouter, Depends

from app.api.deps import BenchmarkServiceDep
from app.core.exceptions import BackendUnavailableError
from app.core.security import require_api_key
from app.schemas.benchmark import BenchmarkRequest, BenchmarkResponse, BenchmarkResult

router = APIRouter(dependencies=[Depends(require_api_key)])


@router.post("", response_model=BenchmarkResponse, summary="Run recall@k benchmark")
async def run_benchmark(
    request: BenchmarkRequest, service: BenchmarkServiceDep
) -> BenchmarkResponse:
    """Execute the labeled query set against each requested backend and return metrics.

    Disabled/unavailable backends are skipped gracefully (reported with -1 metrics) rather
    than failing the whole run. For large runs, enqueue as a background job (see TECH-NOTES).
    """
    results: list[BenchmarkResult] = []
    for backend in request.backends:
        try:
            results.append(
                await service.run(request.cases, backend=backend, k=request.k, mode=request.mode)
            )
        except BackendUnavailableError:
            results.append(
                BenchmarkResult(
                    backend=backend.value,
                    k=request.k,
                    num_queries=len(request.cases),
                    recall_at_k=-1.0,
                    mrr=-1.0,
                    latency_p50_ms=-1.0,
                    latency_p95_ms=-1.0,
                    qps=-1.0,
                )
            )
    return BenchmarkResponse(k=request.k, results=results)
