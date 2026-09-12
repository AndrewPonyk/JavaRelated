"""Thin wrapper around the Elasticsearch client + index mapping."""

from __future__ import annotations

from functools import lru_cache

from django.conf import settings
from elasticsearch import Elasticsearch

PRODUCT_INDEX_MAPPING = {
    "settings": {
        "analysis": {
            "analyzer": {
                "autocomplete": {
                    "tokenizer": "autocomplete",
                    "filter": ["lowercase"],
                }
            },
            "tokenizer": {
                "autocomplete": {
                    "type": "edge_ngram",
                    "min_gram": 2,
                    "max_gram": 20,
                    "token_chars": ["letter", "digit"],
                }
            },
        }
    },
    "mappings": {
        "properties": {
            "name": {"type": "text", "analyzer": "autocomplete", "search_analyzer": "standard"},
            "description": {"type": "text"},
            "sku": {"type": "keyword"},
            "category": {"type": "keyword"},
            "vendor": {"type": "keyword"},
            "price": {"type": "scaled_float", "scaling_factor": 100},
            "popularity": {"type": "rank_feature"},
            "status": {"type": "keyword"},
        }
    },
}


@lru_cache(maxsize=1)
def get_client() -> Elasticsearch:
    return Elasticsearch(settings.ELASTICSEARCH_URL)


def ensure_index() -> None:
    client = get_client()
    index = settings.ELASTICSEARCH_PRODUCT_INDEX
    if not client.indices.exists(index=index):
        client.indices.create(index=index, body=PRODUCT_INDEX_MAPPING)
