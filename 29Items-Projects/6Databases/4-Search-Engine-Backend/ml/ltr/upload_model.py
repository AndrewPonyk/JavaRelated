"""Upload the featureset and trained model to Elasticsearch.

plugin mode — self-managed ES with the community LTR plugin:
    PUT  _ltr                       bootstrap the feature store (idempotent)
    PUT  _ltr/_featureset/<fs>      elasticsearch/ltr/featureset.json
    POST _ltr/_featureset/<fs>/_createmodel   xgboost-json model definition

native mode — Elastic Cloud / ES >= 8.12 (eland import, works without plugins).

Usage: python ml/ltr/upload_model.py --mode plugin --model model.json --name products_ltr_v1
"""

import argparse
import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
FEATURESET_FILE = REPO_ROOT / "elasticsearch" / "ltr" / "featureset.json"

try:
    import httpx
except ImportError as exc:  # pragma: no cover - environment guard
    sys.exit(f"Missing dependency ({exc.name}). Install with: pip install -e 'backend[dev]'")


def upload_plugin(es_url: str, api_key: str | None, model_path: str, model_name: str) -> None:
    featureset = json.loads(FEATURESET_FILE.read_text(encoding="utf-8"))
    featureset_name = featureset["featureset"]["name"]
    headers = {"Content-Type": "application/json"}
    if api_key:
        headers["Authorization"] = f"ApiKey {api_key}"

    with httpx.Client(base_url=es_url, headers=headers, timeout=30) as client:
        response = client.put("/_ltr")
        if response.status_code not in (200, 201, 400):  # 400 = store already exists
            sys.exit(f"_ltr init failed: {response.status_code} {response.text}")
        print("feature store ready")

        response = client.put(f"/_ltr/_featureset/{featureset_name}", json=featureset)
        response.raise_for_status()
        print(f"featureset '{featureset_name}' uploaded")

        # Replace an existing model of the same name (models are immutable).
        client.delete(f"/_ltr/_model/{model_name}")
        response = client.post(
            f"/_ltr/_featureset/{featureset_name}/_createmodel",
            json={
                "model": {
                    "name": model_name,
                    "model": {
                        "type": "model/xgboost+json",
                        "definition": Path(model_path).read_text(encoding="utf-8"),
                    },
                }
            },
        )
        response.raise_for_status()
        print(f"model '{model_name}' created — set LTR_MODE=plugin to serve it")


def upload_native(es_url: str, api_key: str | None, model_path: str, model_name: str) -> None:
    try:
        import xgboost as xgb
        from eland.ml import MLModel
        from eland.ml.ltr import LTRModelConfig, QueryFeatureExtractor

        from elasticsearch import Elasticsearch
    except ImportError as exc:
        sys.exit(f"Missing dependency ({exc.name}). Install with: pip install -e 'backend[ml]'")

    featureset = json.loads(FEATURESET_FILE.read_text(encoding="utf-8"))["featureset"]
    config = LTRModelConfig(
        feature_extractors=[
            QueryFeatureExtractor(feature_name=f["name"], query=f["template"])
            for f in featureset["features"]
        ]
    )

    ranker = xgb.XGBRanker()
    ranker.load_model(model_path)

    es = Elasticsearch(es_url, api_key=api_key)
    MLModel.import_ltr_model(
        es_client=es,
        model_id=model_name,
        model=ranker,
        ltr_model_config=config,
        es_if_exists="replace",
    )
    print(f"model '{model_name}' imported via eland — set LTR_MODE=native to serve it")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=["plugin", "native"], default="plugin")
    parser.add_argument("--model", default="model.json")
    parser.add_argument("--name", default="products_ltr_v1")
    parser.add_argument("--es-url", default="http://localhost:9200")
    parser.add_argument("--api-key", default=None)
    args = parser.parse_args()
    if args.mode == "plugin":
        upload_plugin(args.es_url, args.api_key, args.model, args.name)
    else:
        upload_native(args.es_url, args.api_key, args.model, args.name)
