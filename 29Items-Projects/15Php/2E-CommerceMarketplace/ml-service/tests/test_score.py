"""Contract + fail-safe tests for the fraud service."""

from fastapi.testclient import TestClient

from src.main import app

client = TestClient(app)


def test_health_ok() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_score_returns_probability_in_range() -> None:
    response = client.post(
        "/score",
        json={"features": {"amount_minor": 50000, "item_count": 2, "distinct_sellers": 1}},
    )
    assert response.status_code == 200
    risk = response.json()["risk"]
    assert 0.0 <= risk <= 1.0


def test_high_amount_many_sellers_scores_higher() -> None:
    low = client.post(
        "/score",
        json={"features": {"amount_minor": 1000, "item_count": 1, "distinct_sellers": 1}},
    ).json()["risk"]
    high = client.post(
        "/score",
        json={"features": {"amount_minor": 500000, "item_count": 25, "distinct_sellers": 6}},
    ).json()["risk"]
    assert high > low
