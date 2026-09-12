"""Pagination and error-handling API tests."""

from __future__ import annotations

CONTRACT = b"The termination clause allows either party to terminate with thirty days notice."


def _upload(client, headers, name):
    return client.post(
        "/api/v1/documents",
        headers=headers,
        files={"file": (name, CONTRACT, "text/plain")},
        data={"doc_type": "legal"},
    )


def test_documents_pagination(client, auth_headers) -> None:
    for i in range(3):
        assert _upload(client, auth_headers, f"doc{i}.txt").status_code == 202

    page1 = client.get("/api/v1/documents?limit=2&offset=0", headers=auth_headers).json()
    assert page1["total"] == 3
    assert page1["limit"] == 2
    assert len(page1["items"]) == 2

    page2 = client.get("/api/v1/documents?limit=2&offset=2", headers=auth_headers).json()
    assert page2["total"] == 3
    assert len(page2["items"]) == 1


def test_pagination_validation(client, auth_headers) -> None:
    # limit must be >= 1, offset >= 0
    assert client.get("/api/v1/documents?limit=0", headers=auth_headers).status_code == 422
    assert client.get("/api/v1/documents?offset=-1", headers=auth_headers).status_code == 422


def test_unexpected_error_returns_opaque_500(fastapi_app, auth_headers, monkeypatch) -> None:
    from fastapi.testclient import TestClient

    from app.services.rag_service import RAGService

    async def boom(self, **kwargs):  # noqa: ANN001
        raise ValueError("kaboom with secret internals")

    monkeypatch.setattr(RAGService, "answer_question", boom)

    with TestClient(fastapi_app, raise_server_exceptions=False) as c:
        resp = c.post("/api/v1/query", headers=auth_headers, json={"question": "x"})

    assert resp.status_code == 500
    body = resp.json()
    assert body["detail"] == "internal server error"  # no internals leaked
    assert "kaboom" not in resp.text
    assert "request_id" in body
