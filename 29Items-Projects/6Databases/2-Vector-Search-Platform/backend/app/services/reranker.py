"""Lightweight, dependency-free reranker (feature-flagged via ``ENABLE_RERANKER``).

A real reranking signal without a cross-encoder download: blend the retriever's rank prior
with lexical coverage (fraction of query terms present in the result text). This reliably
promotes results that actually contain the query terms. Swap in a cross-encoder later behind
the same ``rerank`` function.
"""

from __future__ import annotations

import re

from app.vectorstores.base import SearchHit

_TOKEN_RE = re.compile(r"[a-z0-9]+")


def _terms(text: str) -> set[str]:
    return {t for t in _TOKEN_RE.findall(text.lower()) if len(t) > 1}


def lexical_rerank(query: str, hits: list[SearchHit], *, alpha: float = 0.6) -> list[SearchHit]:
    """Reorder ``hits`` by blending original rank with query-term coverage.

    ``alpha`` weights lexical coverage vs. the retriever's rank prior (0..1).
    """
    q_terms = _terms(query)
    if not q_terms or not hits:
        return hits

    n = len(hits)
    reranked: list[SearchHit] = []
    for rank, hit in enumerate(hits):
        coverage = len(q_terms & _terms(hit.text)) / len(q_terms)
        rank_prior = 1.0 - (rank / n)  # 1.0 for the top hit, decreasing downwards
        blended = alpha * coverage + (1 - alpha) * rank_prior
        reranked.append(SearchHit(id=hit.id, score=blended, text=hit.text, metadata=hit.metadata))
    reranked.sort(key=lambda h: h.score, reverse=True)
    return reranked
