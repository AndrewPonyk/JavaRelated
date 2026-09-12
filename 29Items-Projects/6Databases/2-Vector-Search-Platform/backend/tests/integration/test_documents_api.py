"""End-to-end document lifecycle: ingest -> list -> get -> search -> update -> delete."""

from __future__ import annotations

import pytest

_DOC = (
    "Vector databases power semantic search over embeddings. "
    "pgvector, Pinecone, Weaviate, and Milvus store dense vectors and run approximate "
    "nearest-neighbor queries. Hybrid search combines keyword and vector retrieval."
)


@pytest.mark.asyncio
async def test_full_document_lifecycle(client) -> None:
    # 1) ingest
    resp = await client.post("/api/v1/documents", json={"text": _DOC, "source": "unit-test"})
    assert resp.status_code == 201, resp.text
    body = resp.json()
    doc_id = body["document_id"]
    assert body["chunks_indexed"] >= 1
    assert body["backend"] == "memory"

    # 2) list
    resp = await client.get("/api/v1/documents")
    assert resp.status_code == 200
    listing = resp.json()
    assert listing["total"] == 1
    assert listing["items"][0]["id"] == doc_id

    # 3) get detail
    resp = await client.get(f"/api/v1/documents/{doc_id}")
    assert resp.status_code == 200
    detail = resp.json()
    assert detail["text"] == _DOC
    assert detail["num_chunks"] >= 1

    # 4) search finds this document
    resp = await client.post(
        "/api/v1/search",
        json={"query": "vector database semantic search", "k": 5, "mode": "hybrid"},
    )
    assert resp.status_code == 200
    results = resp.json()["results"]
    assert results, "expected at least one hit"
    assert any(r["metadata"].get("document_id") == doc_id for r in results)

    # 5) update / re-index
    resp = await client.put(
        f"/api/v1/documents/{doc_id}", json={"text": "Completely new content about databases."}
    )
    assert resp.status_code == 200

    # 6) delete + confirm gone
    resp = await client.delete(f"/api/v1/documents/{doc_id}")
    assert resp.status_code == 204
    resp = await client.get(f"/api/v1/documents/{doc_id}")
    assert resp.status_code == 404
    assert resp.json()["error"]["code"] == "not_found"


@pytest.mark.asyncio
async def test_ingest_validation_rejects_empty_text(client) -> None:
    resp = await client.post("/api/v1/documents", json={"text": ""})
    assert resp.status_code == 422


@pytest.mark.asyncio
async def test_vector_and_keyword_modes(client) -> None:
    await client.post("/api/v1/documents", json={"text": _DOC, "source": "modes"})
    for mode in ("vector", "keyword", "hybrid"):
        resp = await client.post(
            "/api/v1/search", json={"query": "hybrid vector retrieval", "k": 3, "mode": mode}
        )
        assert resp.status_code == 200, mode
        assert resp.json()["mode"] == mode
