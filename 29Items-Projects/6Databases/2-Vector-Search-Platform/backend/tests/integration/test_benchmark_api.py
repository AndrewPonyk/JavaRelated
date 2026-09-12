"""Integration test for the benchmark endpoint (recall@k over a small labeled set)."""

from __future__ import annotations

import pytest

_DOCS = {
    "vectors": "Vector databases store embeddings and run nearest neighbor similarity search.",
    "cooking": "Italian pasta recipes use tomatoes, basil, garlic, and olive oil.",
    "finance": "Portfolio optimization balances risk and return across many assets.",
}


async def _ingest_all(client) -> dict[str, str]:
    ids: dict[str, str] = {}
    for key, text in _DOCS.items():
        resp = await client.post("/api/v1/documents", json={"text": text, "source": key})
        assert resp.status_code == 201
        ids[key] = resp.json()["document_id"]
    return ids


@pytest.mark.asyncio
async def test_benchmark_recall_on_memory_backend(client) -> None:
    ids = await _ingest_all(client)
    payload = {
        "backends": ["memory"],
        "k": 5,
        "mode": "vector",
        "cases": [
            {
                "query": "embeddings nearest neighbor vector search",
                "relevant_ids": [ids["vectors"]],
            },
            {"query": "italian pasta tomatoes basil recipe", "relevant_ids": [ids["cooking"]]},
        ],
    }
    resp = await client.post("/api/v1/benchmarks", json=payload)
    assert resp.status_code == 200, resp.text
    result = resp.json()["results"][0]
    assert result["backend"] == "memory"
    assert result["num_queries"] == 2
    assert result["recall_at_k"] == pytest.approx(1.0)
    assert result["mrr"] > 0.0
    assert result["qps"] > 0.0


@pytest.mark.asyncio
async def test_benchmark_skips_disabled_backend(client) -> None:
    await _ingest_all(client)
    payload = {
        "backends": ["pinecone"],
        "k": 3,
        "cases": [{"query": "anything", "relevant_ids": ["x"]}],
    }
    resp = await client.post("/api/v1/benchmarks", json=payload)
    assert resp.status_code == 200
    # disabled backend is reported with sentinel -1 metrics, not a hard failure.
    assert resp.json()["results"][0]["recall_at_k"] == -1.0
