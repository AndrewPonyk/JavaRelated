"""End-to-end API tests covering the main flows through the real local backend."""

from __future__ import annotations

from tests.conftest import headers_for

CONTRACT = (
    b"This Master Services Agreement is between Acme Corp and the Client. "
    b"The termination clause allows either party to terminate with thirty days notice. "
    b"Payment is due within fifteen days of invoice."
)


def _upload(client, headers, name="msa.txt", doc_type="legal", body=CONTRACT):
    return client.post(
        "/api/v1/documents",
        headers=headers,
        files={"file": (name, body, "text/plain")},
        data={"doc_type": doc_type},
    )


def test_upload_indexes_inline_and_lists(client, auth_headers) -> None:
    resp = _upload(client, auth_headers)
    assert resp.status_code == 202
    doc_id = resp.json()["document_id"]

    # Inline ingestion → already INDEXED with chunks.
    got = client.get(f"/api/v1/documents/{doc_id}", headers=auth_headers)
    assert got.status_code == 200
    body = got.json()
    assert body["status"] == "indexed"
    assert body["chunk_count"] >= 1

    listing = client.get("/api/v1/documents", headers=auth_headers)
    assert listing.status_code == 200
    assert listing.json()["total"] == 1


def test_query_returns_grounded_answer_with_citations(client, auth_headers) -> None:
    _upload(client, auth_headers)
    resp = client.post(
        "/api/v1/query",
        headers=auth_headers,
        json={"question": "What is the termination notice period?"},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["citations"], "expected at least one citation"
    assert body["query_id"]
    assert "notice" in body["answer"].lower() or "thirty" in body["answer"].lower()


def test_tenant_isolation(client) -> None:
    _upload(client, headers_for("tenant-a"))
    # tenant-b sees no documents and gets a grounded "don't know".
    listing = client.get("/api/v1/documents", headers=headers_for("tenant-b"))
    assert listing.json()["total"] == 0

    resp = client.post(
        "/api/v1/query",
        headers=headers_for("tenant-b"),
        json={"question": "termination notice period"},
    )
    assert resp.status_code == 200
    assert resp.json()["citations"] == []
    assert "don't know" in resp.json()["answer"].lower()


def test_summarize_endpoint(client, auth_headers) -> None:
    doc_id = _upload(client, auth_headers).json()["document_id"]
    resp = client.post(f"/api/v1/documents/{doc_id}/summarize", headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["document_id"] == doc_id
    assert resp.json()["summary"]


def test_feedback_flow(client, auth_headers) -> None:
    _upload(client, auth_headers)
    q = client.post("/api/v1/query", headers=auth_headers, json={"question": "termination"}).json()
    fb = client.post(
        f"/api/v1/query/{q['query_id']}/feedback", headers=auth_headers, json={"value": 1}
    )
    assert fb.status_code == 204


def test_delete_document(client, auth_headers) -> None:
    doc_id = _upload(client, auth_headers).json()["document_id"]
    deleted = client.delete(f"/api/v1/documents/{doc_id}", headers=auth_headers)
    assert deleted.status_code == 204
    assert client.get(f"/api/v1/documents/{doc_id}", headers=auth_headers).status_code == 404


def test_streaming_query_emits_sse(client, auth_headers) -> None:
    _upload(client, auth_headers)
    resp = client.post(
        "/api/v1/query/stream", headers=auth_headers, json={"question": "termination notice"}
    )
    assert resp.status_code == 200
    assert resp.headers["content-type"].startswith("text/event-stream")
    text = resp.text
    assert "event: token" in text
    assert "event: citations" in text
    assert "event: done" in text


def test_auth_required(client) -> None:
    assert client.get("/api/v1/documents").status_code == 401


def test_scope_enforced(client) -> None:
    # A read-only principal cannot upload.
    read_only = headers_for("tenant-a", scopes=("documents:read",))
    resp = _upload(client, read_only)
    assert resp.status_code == 403


def test_invalid_doc_type_rejected(client, auth_headers) -> None:
    resp = _upload(client, auth_headers, doc_type="invalid")
    assert resp.status_code == 422


def test_empty_file_rejected(client, auth_headers) -> None:
    resp = _upload(client, auth_headers, body=b"")
    assert resp.status_code == 422
