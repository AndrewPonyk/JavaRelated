"""Tests for the severity model (rule-based + trained-estimator branches)."""

from app.core import config
from app.ml.model import SeverityModel, _score_to_distribution
from app.models.common import Severity


def test_distribution_sums_to_one():
    dist = _score_to_distribution(0.5)
    assert len(dist) == 4
    assert abs(sum(dist) - 1.0) < 1e-9


def test_rule_based_high_risk():
    model = SeverityModel()  # rule-based by default
    severity, confidence = model.predict([3, 1, 0.95])
    assert severity in (Severity.MAJOR, Severity.CONTRAINDICATED)
    assert 0.0 < confidence <= 1.0


def test_no_graph_evidence_is_unknown():
    # No shared classes, no path, no embedding similarity -> not an interaction.
    model = SeverityModel()
    severity, confidence = model.predict([0, -1, 0.0])
    assert severity == Severity.UNKNOWN
    assert confidence < 0.2


def test_shared_class_is_at_least_moderate():
    # Two ingredients sharing a class are 2 hops apart -> real signal.
    model = SeverityModel()
    severity, confidence = model.predict([1, 2, 0.0])
    assert severity in (Severity.MODERATE, Severity.MAJOR, Severity.CONTRAINDICATED)
    assert confidence >= 0.35


def test_predict_proba_length():
    assert len(SeverityModel().predict_proba([0, 0, 0])) == 4


def test_load_falls_back_to_rules_when_no_artifact():
    model = SeverityModel()
    model.load()
    assert model.mode == "rule_based"


def test_load_falls_back_when_artifact_unreadable(tmp_path):
    bad = tmp_path / "model.joblib"
    bad.write_text("not a real model")
    settings = config.get_settings()
    original = settings.ml_model_local_path
    settings.ml_model_local_path = str(bad)
    try:
        model = SeverityModel()
        model.load()
        assert model.mode == "rule_based"
    finally:
        settings.ml_model_local_path = original


def test_trained_estimator_is_used():
    model = SeverityModel()

    class _Estimator:
        def predict_proba(self, X):
            return [[0.05, 0.05, 0.1, 0.8]]

    model._estimator = _Estimator()
    severity, confidence = model.predict([1, 1, 1])
    assert severity == Severity.CONTRAINDICATED
    assert confidence == 0.8
