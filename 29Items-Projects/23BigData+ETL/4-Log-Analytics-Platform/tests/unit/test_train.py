"""Training pipeline: synthetic bootstrap, OpenSearch fetching, holdout evaluation."""

from __future__ import annotations

import json

import httpx
import pytest

pytest.importorskip("sklearn")

from log_analytics.ml.features import build_features
from log_analytics.ml.synthetic import generate_log_records
from log_analytics.ml.train import fetch_opensearch, main, train


def test_synthetic_generator_shape_and_determinism() -> None:
    a = generate_log_records(minutes=30, events_per_minute=10, seed=5)
    b = generate_log_records(minutes=30, events_per_minute=10, seed=5)
    assert list(a.columns) == ["timestamp", "service", "level", "message"]
    assert len(a) > 300
    assert a["message"].equals(b["message"])  # deterministic per seed


def test_incident_minutes_look_like_outages() -> None:
    records = generate_log_records(
        minutes=60, events_per_minute=20, incident_minutes={"checkout": [30]}
    )
    checkout = records[records["service"] == "checkout"].set_index("timestamp")
    per_minute = checkout.resample("1min")["level"].agg(
        volume="size", errors=lambda s: s.isin(["ERROR", "FATAL"]).sum()
    )
    incident_row = per_minute.iloc[30]
    assert incident_row["volume"] > 3 * per_minute["volume"].median()
    assert incident_row["errors"] / incident_row["volume"] > 0.2


def test_train_produces_holdout_metrics() -> None:
    records = generate_log_records(minutes=150, events_per_minute=12)
    detector, metadata = train(records, window="1min", contamination="auto")
    assert metadata.n_samples > 0
    assert metadata.metrics["holdout_windows"] > 0
    assert 0.0 <= metadata.metrics["holdout_score_mean"] <= 1.0
    scores = detector.score(build_features(records))
    assert scores.shape[0] > 100


def test_train_refuses_thin_history() -> None:
    records = generate_log_records(minutes=5, events_per_minute=10)
    with pytest.raises(SystemExit, match="refusing to train"):
        train(records, window="1min", contamination="auto")


def test_fetch_opensearch_paginates_with_search_after() -> None:
    pages = [
        [
            {
                "_id": f"id{i}",
                "sort": [i, f"id{i}"],
                "_source": {
                    "@timestamp": f"2026-07-01T12:00:{i:02d}Z",
                    "service": "checkout",
                    "level": "INFO",
                    "message": f"m{i}",
                },
            }
            for i in range(2)
        ],
        [],
    ]
    bodies: list[dict] = []

    def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content)
        bodies.append(body)
        return httpx.Response(200, json={"hits": {"hits": pages[len(bodies) - 1]}})

    frame = fetch_opensearch(
        "http://opensearch:9200",
        from_ts="2026-07-01",
        to_ts=None,
        transport=httpx.MockTransport(handler),
    )
    assert len(frame) == 2
    assert list(frame.columns) == ["timestamp", "service", "level", "message"]
    assert "search_after" not in bodies[0]
    assert bodies[1]["search_after"] == [1, "id1"]  # cursor from the last hit
    assert bodies[0]["query"]["range"]["@timestamp"]["gte"] == "2026-07-01"


def test_main_synthetic_end_to_end(tmp_path) -> None:
    version = main(["--source", "synthetic", "--minutes", "150", "--registry", str(tmp_path)])
    assert (tmp_path / version / "model.joblib").exists()
    assert (tmp_path / "latest.json").exists()
