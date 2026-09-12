"""E2E smoke: the whole pipeline, outside-in, against the compose stack.

Prerequisites (each test skips with a precise reason when its piece isn't running):
    docker compose up -d kafka opensearch dashboards redis
    python scripts/es_migrate.py && python scripts/create_kafka_topics.py
    uvicorn log_analytics.ingestion.gateway:app --port 8080     (or --profile app)
    docker compose --profile pipeline up -d                     (Spark jobs)
    python -m log_analytics.alerting.engine                     (for the alert test)

Run: pytest -m e2e
"""

from __future__ import annotations

import os
import time
import uuid

import httpx
import pytest

pytestmark = pytest.mark.e2e

GATEWAY_URL = os.getenv("LA_E2E_GATEWAY", "http://localhost:8080")
OPENSEARCH_URL = os.getenv("LA_OPENSEARCH_URL", "http://localhost:9200")

INDEX_WAIT_SECONDS = int(os.getenv("LA_E2E_TIMEOUT", "120"))


def _up(url: str) -> bool:
    try:
        return httpx.get(url, timeout=3.0).status_code == 200
    except httpx.HTTPError:
        return False


def _count(client: httpx.Client, index: str, query: dict) -> int:
    resp = client.post(f"{OPENSEARCH_URL}/{index}/_count", json={"query": query})
    if resp.status_code == 404:  # index not created yet
        return 0
    resp.raise_for_status()
    return int(resp.json()["count"])


def _wait_for_count(index: str, query: dict, minimum: int, timeout: int) -> int:
    deadline = time.monotonic() + timeout
    count = 0
    with httpx.Client(timeout=10.0) as client:
        while time.monotonic() < deadline:
            count = _count(client, index, query)
            if count >= minimum:
                return count
            time.sleep(3)
    return count


@pytest.fixture(scope="module")
def stack() -> None:
    if not _up(f"{OPENSEARCH_URL}/_cluster/health"):
        pytest.skip(f"OpenSearch not reachable at {OPENSEARCH_URL}")
    if not _up(f"{GATEWAY_URL}/healthz"):
        pytest.skip(f"ingestion gateway not reachable at {GATEWAY_URL}")


def _batch(service: str) -> list[dict]:
    """Deterministic batch (fixed timestamps) so a resend proves idempotent indexing."""
    base = "2026-07-08T10:{m:02d}:{s:02d}Z"
    events = []
    for i in range(30):
        events.append(
            {
                "timestamp": base.format(m=i // 10, s=i % 60),
                "service": service,
                "level": ["info", "warning", "error"][i % 3],  # aliases on purpose
                "message": f"request completed id={i} user 'alice' duration_ms={i * 7}",
                "host": "e2e-host-1",
                "attributes": {"region": "eu-central-1"},
            }
        )
    return events


def test_ingest_to_search_with_idempotent_reindex(stack: None) -> None:
    service = f"e2e-{uuid.uuid4().hex[:8]}"
    batch = _batch(service)

    resp = httpx.post(f"{GATEWAY_URL}/v1/logs", json=batch, timeout=10.0)
    assert resp.status_code == 202
    assert resp.json() == {"accepted": 30, "rejected": 0, "errors": []}

    query = {"term": {"service": service}}
    count = _wait_for_count("la-logs", query, minimum=30, timeout=INDEX_WAIT_SECONDS)
    if count == 0:
        pytest.skip(
            "gateway accepted the batch but nothing reached la-logs — "
            "start the enrich job: docker compose --profile pipeline up -d spark-enrich"
        )
    assert count == 30

    # Enrichment happened: levels normalized, template + redaction fields present.
    with httpx.Client(timeout=10.0) as client:
        resp = client.post(
            f"{OPENSEARCH_URL}/la-logs/_search",
            json={"query": query, "size": 1, "sort": [{"@timestamp": "asc"}]},
        )
        doc = resp.json()["hits"]["hits"][0]["_source"]
    assert doc["level"] in {"INFO", "WARN", "ERROR"}  # "warning" alias → WARN
    assert doc["template_id"]
    assert doc["@timestamp"].startswith("2026-07-08T10:")

    # Effectively-once: resending the identical batch must not create duplicates.
    resp = httpx.post(f"{GATEWAY_URL}/v1/logs", json=batch, timeout=10.0)
    assert resp.status_code == 202
    time.sleep(15)  # one indexing trigger interval
    assert _wait_for_count("la-logs", query, minimum=30, timeout=30) == 30


def test_anomaly_scores_flow(stack: None) -> None:
    count = _wait_for_count("la-anomalies", {"match_all": {}}, minimum=1, timeout=30)
    if count == 0:
        pytest.skip(
            "no anomaly documents — start the scoring job "
            "(train first: python -m log_analytics.ml.train --source synthetic; "
            "then: docker compose --profile pipeline up -d spark-anomaly)"
        )
    with httpx.Client(timeout=10.0) as client:
        resp = client.post(
            f"{OPENSEARCH_URL}/la-anomalies/_search",
            json={"size": 5, "sort": [{"window_start": "desc"}]},
        )
    for hit in resp.json()["hits"]["hits"]:
        doc = hit["_source"]
        assert 0.0 <= doc["score"] <= 1.0
        assert doc["model_version"]
        assert doc["service"]


def test_alerts_recorded(stack: None) -> None:
    count = _wait_for_count("la-alerts", {"match_all": {}}, minimum=1, timeout=30)
    if count == 0:
        pytest.skip(
            "no alert documents — run the alerting engine and generate a burst: "
            "python scripts/seed_sample_logs.py --count 2000 --burst-errors"
        )
    with httpx.Client(timeout=10.0) as client:
        resp = client.post(
            f"{OPENSEARCH_URL}/la-alerts/_search",
            json={"size": 3, "sort": [{"@timestamp": "desc"}]},
        )
    for hit in resp.json()["hits"]["hits"]:
        doc = hit["_source"]
        assert doc["rule_id"]
        assert doc["severity"] in {"info", "warning", "critical"}
        assert doc["dedup_key"]
