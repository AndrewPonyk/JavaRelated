"""Unit tests for the feature pipeline and the heuristic fallback model."""

from src.model import Features, FraudModel


def test_features_parse_defaults_for_missing_fields() -> None:
    features = Features.from_payload({})
    assert features.amount_minor == 0
    assert features.item_count == 0
    assert features.distinct_sellers == 0
    assert features.currency == "USD"


def test_feature_vector_column_order_is_stable() -> None:
    features = Features(amount_minor=1000, item_count=3, distinct_sellers=2, currency="USD")
    assert features.to_vector() == [1000.0, 3.0, 2.0]


def test_model_falls_back_to_heuristic_without_an_artifact() -> None:
    model = FraudModel()
    assert model.version == "heuristic-0.1.0"


def test_heuristic_scores_are_bounded_and_monotonic() -> None:
    model = FraudModel()
    low = model.score(Features(1_000, 1, 1, "USD"))
    high = model.score(Features(500_000, 25, 6, "USD"))
    assert 0.0 <= low <= 1.0
    assert 0.0 <= high <= 1.0
    assert high > low
