"""Unit tests for Reciprocal Rank Fusion."""

from __future__ import annotations

from app.services.search_service import reciprocal_rank_fusion
from app.vectorstores.base import SearchHit


def _hit(hid: str) -> SearchHit:
    return SearchHit(id=hid, score=0.0)


def test_rrf_rewards_agreement_across_rankings() -> None:
    vector_ranking = [_hit("a"), _hit("b"), _hit("c")]
    keyword_ranking = [_hit("b"), _hit("a"), _hit("d")]

    fused = reciprocal_rank_fusion([vector_ranking, keyword_ranking])
    ids = [h.id for h in fused]

    # 'a' and 'b' appear high in both lists, so they should top the fused ranking.
    assert set(ids[:2]) == {"a", "b"}
    # every id from both lists survives the union
    assert set(ids) == {"a", "b", "c", "d"}


def test_rrf_scores_sorted_descending() -> None:
    fused = reciprocal_rank_fusion([[_hit("a"), _hit("b")], [_hit("a")]])
    scores = [h.score for h in fused]
    assert scores == sorted(scores, reverse=True)
    # 'a' is rank-1 in both lists -> strictly highest fused score.
    assert fused[0].id == "a"
