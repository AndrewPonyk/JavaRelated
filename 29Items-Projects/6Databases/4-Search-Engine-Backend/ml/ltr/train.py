"""Train the LTR ranking model (XGBoost LambdaMART).

Feature values are produced by executing elasticsearch/ltr/featureset.json against
the live index for each (query, product) judgment pair — the exact templates the
runtime rescorer executes, which eliminates training/serving skew.

The model is exported only if it beats the BM25 baseline (name_bm25 feature) on
validation nDCG@10.

Usage: python ml/ltr/train.py --judgments judgments.tsv --output model.json
       (deps: pip install -e "backend[ml]")
"""

import argparse
import json
import math
import random
import sys
from collections import defaultdict
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
FEATURESET = REPO_ROOT / "elasticsearch" / "ltr" / "featureset.json"

try:
    import numpy as np
    import xgboost as xgb

    from elasticsearch import Elasticsearch
except ImportError as exc:  # pragma: no cover - environment guard
    sys.exit(f"Missing dependency ({exc.name}). Install with: pip install -e 'backend[ml]'")


def load_judgments(path: str) -> dict[int, dict]:
    """qid -> {"keywords": str, "docs": [(product_id, grade), ...]}"""
    groups: dict[int, dict] = defaultdict(lambda: {"keywords": "", "docs": []})
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        grade_s, qid_s, keywords, product_id = line.split("\t")
        qid = int(qid_s.removeprefix("qid:"))
        groups[qid]["keywords"] = keywords
        groups[qid]["docs"].append((product_id, int(grade_s)))
    return dict(groups)


def render_template(template: dict, keywords: str) -> dict:
    """Substitute the {{keywords}} mustache param with proper JSON escaping."""
    escaped = json.dumps(keywords)[1:-1]
    return json.loads(json.dumps(template).replace("{{keywords}}", escaped))


def extract_features(
    es: "Elasticsearch", index: str, features: list[dict], keywords: str, doc_ids: list[str]
) -> "np.ndarray":
    """One column per featureset feature: the ES score of the feature query per doc,
    0.0 where the query does not match (exactly what the LTR plugin logs)."""
    matrix = np.zeros((len(doc_ids), len(features)), dtype=np.float32)
    row_of = {doc_id: i for i, doc_id in enumerate(doc_ids)}
    for col, feature in enumerate(features):
        query = {
            "bool": {
                "must": [render_template(feature["template"], keywords)],
                "filter": [{"terms": {"id": doc_ids}}],
            }
        }
        response = es.search(index=index, query=query, size=len(doc_ids), source=False)
        for hit in response["hits"]["hits"]:
            matrix[row_of[hit["_id"]], col] = hit["_score"] or 0.0
    return matrix


def ndcg_at_k(grades_in_ranked_order: list[int], k: int = 10) -> float:
    def dcg(grades: list[int]) -> float:
        return sum((2**g - 1) / math.log2(i + 2) for i, g in enumerate(grades[:k]))

    ideal = dcg(sorted(grades_in_ranked_order, reverse=True))
    return dcg(grades_in_ranked_order) / ideal if ideal > 0 else 0.0


def mean_ndcg(scores: "np.ndarray", grades: "np.ndarray", qid_bounds: list[tuple[int, int]]) -> float:
    values = []
    for start, end in qid_bounds:
        order = np.argsort(-scores[start:end])
        values.append(ndcg_at_k(list(grades[start:end][order])))
    return float(np.mean(values)) if values else 0.0


def main(judgments_path: str, es_url: str, api_key: str | None, index: str, output: str) -> None:
    featureset = json.loads(FEATURESET.read_text(encoding="utf-8"))["featureset"]
    features = featureset["features"]
    feature_names = [f["name"] for f in features]
    groups = load_judgments(judgments_path)
    print(f"{len(groups)} queries, {sum(len(g['docs']) for g in groups.values())} judgments")

    es = Elasticsearch(es_url, api_key=api_key)

    x_rows, y_rows, qid_rows = [], [], []
    for qid, group in sorted(groups.items()):
        doc_ids = [doc_id for doc_id, _ in group["docs"]]
        x_rows.append(extract_features(es, index, features, group["keywords"], doc_ids))
        y_rows.extend(grade for _, grade in group["docs"])
        qid_rows.extend([qid] * len(doc_ids))

    x = np.vstack(x_rows)
    y = np.asarray(y_rows, dtype=np.float32)
    qid = np.asarray(qid_rows, dtype=np.int32)

    # Split by QUERY (never by row) so validation queries are fully unseen.
    unique_qids = sorted(set(qid_rows))
    random.Random(7).shuffle(unique_qids)
    val_qids = set(unique_qids[: max(1, len(unique_qids) // 5)])
    val_mask = np.isin(qid, list(val_qids))
    train_mask = ~val_mask

    ranker = xgb.XGBRanker(
        objective="rank:ndcg",
        eval_metric="ndcg@10",
        n_estimators=200,
        max_depth=4,
        learning_rate=0.1,
        random_state=7,
    )
    ranker.fit(x[train_mask], y[train_mask], qid=qid[train_mask])

    def bounds(mask: "np.ndarray") -> list[tuple[int, int]]:
        ids = qid[mask]
        edges, start = [], 0
        for i in range(1, len(ids) + 1):
            if i == len(ids) or ids[i] != ids[start]:
                edges.append((start, i))
                start = i
        return edges

    val_bounds = bounds(val_mask)
    model_ndcg = mean_ndcg(ranker.predict(x[val_mask]), y[val_mask], val_bounds)
    bm25_col = feature_names.index("name_bm25")
    baseline_ndcg = mean_ndcg(x[val_mask][:, bm25_col], y[val_mask], val_bounds)
    print(f"validation nDCG@10 — model: {model_ndcg:.4f}, BM25 baseline: {baseline_ndcg:.4f}")

    if model_ndcg < baseline_ndcg:
        sys.exit("REFUSING to export: model does not beat the BM25 baseline.")

    booster = ranker.get_booster()
    booster.feature_names = feature_names  # eland/native import needs named features
    booster.save_model(output)
    print(f"Saved {output} (features: {feature_names})")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--judgments", default="judgments.tsv")
    parser.add_argument("--es-url", default="http://localhost:9200")
    parser.add_argument("--api-key", default=None)
    parser.add_argument("--index", default="products")
    parser.add_argument("--output", default="model.json")
    args = parser.parse_args()
    main(args.judgments, args.es_url, args.api_key, args.index, args.output)
