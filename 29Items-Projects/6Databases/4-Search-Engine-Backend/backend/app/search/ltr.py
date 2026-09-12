"""Learning-to-Rank rescore clause builders.

Two deployment modes (selected via settings.ltr_mode — see TECH-NOTES §3.6 #1):

- "plugin": community `elasticsearch-learning-to-rank` plugin (`sltr` query).
  Requires a self-managed cluster (GKE / docker) — the plugin cannot be installed
  on Elastic Cloud. Featureset lives in elasticsearch/ltr/featureset.json.

- "native": built-in `learning_to_rank` rescorer (ES >= 8.12, works on Elastic Cloud).
  Model is trained in ml/ltr/train.py and uploaded with eland.

Both rescore only the top-`window_size` BM25 hits; if the model is missing the search
falls back to plain BM25 (see search_service error handling).
"""

from typing import Any

PARAM_KEYWORDS = "keywords"  # single query-dependent parameter shared by both modes


def build_ltr_rescore(
    *, mode: str, model_name: str, query_text: str, window_size: int
) -> dict[str, Any] | None:
    if mode == "plugin":
        return {
            "window_size": window_size,
            "query": {
                "rescore_query": {
                    "sltr": {
                        "model": model_name,
                        "params": {PARAM_KEYWORDS: query_text},
                    }
                },
                "query_weight": 1.0,
                "rescore_query_weight": 2.0,
                "score_mode": "total",
            },
        }
    if mode == "native":
        return {
            "window_size": window_size,
            "learning_to_rank": {
                "model_id": model_name,
                "params": {PARAM_KEYWORDS: query_text},
            },
        }
    return None  # mode == "off" (or unknown → fail safe to BM25)
