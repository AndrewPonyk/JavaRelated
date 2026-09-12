"""Tests for the ML service. Run with: pytest (from ml-service/)."""
from fastapi.testclient import TestClient

from app.main import app
from app.sentiment import SentimentAnalyzer

client = TestClient(app)


def test_health_ok():
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "UP"


def test_score_returns_label_and_score():
    resp = client.post(
        "/api/v1/sentiment",
        json={"review_id": "rev-1", "text": "This product is great, I love it"},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["review_id"] == "rev-1"
    assert body["label"] in {"POSITIVE", "NEGATIVE", "NEUTRAL"}
    assert 0.0 <= body["score"] <= 1.0


def test_heuristic_detects_negative():
    # Force the heuristic path so the assertion is deterministic without a model.
    analyzer = SentimentAnalyzer()
    analyzer._pipeline = "heuristic"  # noqa: SLF001 - exercising the fallback
    result = analyzer.analyze("this is terrible and broken, the worst")
    assert result.label == "NEGATIVE"
