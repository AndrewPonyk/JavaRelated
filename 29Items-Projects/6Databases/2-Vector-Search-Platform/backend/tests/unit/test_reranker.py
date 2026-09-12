"""Unit tests for the lexical reranker."""

from __future__ import annotations

from app.services.reranker import lexical_rerank
from app.vectorstores.base import SearchHit


def test_rerank_promotes_query_term_matches() -> None:
    # 'b' contains the query terms but is ranked last by the retriever; rerank should lift it.
    hits = [
        SearchHit(id="a", score=0.9, text="completely unrelated content here"),
        SearchHit(id="c", score=0.8, text="also nothing to do with it"),
        SearchHit(id="b", score=0.1, text="vector database semantic search platform"),
    ]
    reranked = lexical_rerank("vector database search", hits, alpha=0.8)
    assert reranked[0].id == "b"


def test_rerank_noop_without_query_terms() -> None:
    hits = [SearchHit(id="a", score=0.5, text="hello")]
    assert lexical_rerank("", hits) == hits
