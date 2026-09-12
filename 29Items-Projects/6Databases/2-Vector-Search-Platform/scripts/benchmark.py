#!/usr/bin/env python
"""Offline recall@k benchmark harness.

Loads a labeled dataset (JSON: list of {query, relevant_ids}) and runs it against one or more
backends via the BenchmarkService, printing a comparison table. Benchmarks whatever is already
indexed in each backend (ingest documents via the API or a seeding step first).

Usage:
    python scripts/benchmark.py --dataset data/golden.json --backends memory pgvector --k 10
"""
from __future__ import annotations

import argparse
import asyncio
import json
import sys
from pathlib import Path

# Make the backend package importable when run from the repo root.
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "backend"))

from app.core.config import Backend, get_settings  # noqa: E402
from app.schemas.benchmark import QueryCase  # noqa: E402
from app.services.benchmark_service import BenchmarkService  # noqa: E402
from app.services.embedding_service import EmbeddingService  # noqa: E402
from app.services.search_service import SearchService  # noqa: E402


def load_cases(path: Path) -> list[QueryCase]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    return [
        QueryCase(query=item["query"], relevant_ids=item.get("relevant_ids", [])) for item in raw
    ]


async def main() -> None:
    parser = argparse.ArgumentParser(description="Run recall@k benchmarks across backends.")
    parser.add_argument("--dataset", type=Path, required=True, help="Path to labeled JSON dataset.")
    parser.add_argument("--backends", nargs="+", default=["memory"], help="Backends to compare.")
    parser.add_argument("--k", type=int, default=10)
    args = parser.parse_args()

    cases = load_cases(args.dataset)
    settings = get_settings()
    embedder = EmbeddingService(settings)
    # No DB session here -> vector-only benchmark (keyword arm needs the repository).
    search = SearchService(embedder, repository=None, settings=settings)
    service = BenchmarkService(search)

    print(f"{'backend':<10} {'recall@k':>9} {'mrr':>7} {'p50(ms)':>9} {'p95(ms)':>9} {'qps':>7}")
    print("-" * 56)
    for name in args.backends:
        result = await service.run(cases, backend=Backend(name), k=args.k)
        print(
            f"{result.backend:<10} {result.recall_at_k:>9.3f} {result.mrr:>7.3f} "
            f"{result.latency_p50_ms:>9.1f} {result.latency_p95_ms:>9.1f} {result.qps:>7.1f}"
        )


if __name__ == "__main__":
    asyncio.run(main())
