"""Bootstrap search assets (idempotent — safe to run on every deploy):

1. Upload elasticsearch/synonyms/synonyms_en.txt as synonyms set "products-synonyms".
2. Create the versioned index (products_v1) from elasticsearch/indexes/products.index.json
   if it does not exist.
3. Point the read/write alias ("products") at it.

Usage: python scripts/create_indexes.py [--es-url http://localhost:9200] [--version 1]
"""

import argparse
import json
from pathlib import Path

from elasticsearch import Elasticsearch

REPO_ROOT = Path(__file__).resolve().parents[1]
INDEX_SPEC = REPO_ROOT / "elasticsearch" / "indexes" / "products.index.json"
SYNONYMS_FILE = REPO_ROOT / "elasticsearch" / "synonyms" / "synonyms_en.txt"
SYNONYMS_SET_NAME = "products-synonyms"
ALIAS = "products"


def load_synonym_rules() -> list[dict[str, str]]:
    rules = []
    for i, line in enumerate(SYNONYMS_FILE.read_text(encoding="utf-8").splitlines()):
        line = line.strip()
        if line and not line.startswith("#"):
            rules.append({"id": f"rule-{i}", "synonyms": line})
    return rules


def main(es_url: str, api_key: str | None, version: int) -> None:
    es = Elasticsearch(es_url, api_key=api_key)
    index_name = f"{ALIAS}_v{version}"

    print(f"Uploading synonyms set '{SYNONYMS_SET_NAME}' ({SYNONYMS_FILE.name})")
    es.synonyms.put_synonym(id=SYNONYMS_SET_NAME, synonyms_set=load_synonym_rules())

    # The alias is the source of truth: if it exists, some products_vN is already
    # serving (possibly a later version from scripts/reindex.py) — leave it alone.
    if es.indices.exists_alias(name=ALIAS):
        current = ", ".join(es.indices.get_alias(name=ALIAS))
        print(f"Alias '{ALIAS}' already serves [{current}] — synonyms refreshed, done.")
        return

    if not es.indices.exists(index=index_name):
        spec = json.loads(INDEX_SPEC.read_text(encoding="utf-8"))
        print(f"Creating index {index_name}")
        es.indices.create(index=index_name, settings=spec["settings"], mappings=spec["mappings"])

    print(f"Pointing alias '{ALIAS}' -> {index_name}")
    es.indices.put_alias(index=index_name, name=ALIAS)
    print("Done.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--es-url", default="http://localhost:9200")
    parser.add_argument("--api-key", default=None, help="ES API key (staging/prod)")
    parser.add_argument("--version", type=int, default=1)
    args = parser.parse_args()
    main(es_url=args.es_url, api_key=args.api_key, version=args.version)
