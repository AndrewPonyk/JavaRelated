"""Tests: ML severity classifier — features, rules fallback, trained artifact."""

from __future__ import annotations

import math

from app.core.config import settings
from app.models.finding import Severity
from app.services.ml_classifier import SeverityClassifier, _rule_based
from app.services.ml_features import FEATURE_NAMES, extract_features


class TestFeatures:
    def test_shape_and_finiteness(self):
        vector = extract_features(
            source="zap",
            rule_id="40018",
            title="SQL injection in id",
            description="database error",
            param="id",
            method="GET",
        )
        assert len(FEATURE_NAMES) == 24  # 3 source + 8 buckets + 5 text + 8 keywords
        assert len(vector) == len(FEATURE_NAMES)
        assert all(math.isfinite(x) for x in vector)

    def test_source_one_hot(self):
        vec_zap = extract_features(source="zap", rule_id="x", title="", description="")
        vec_sql = extract_features(source="sqlmap", rule_id="x", title="", description="")
        assert vec_zap[:3] == [1.0, 0.0, 0.0]
        assert vec_sql[:3] == [0.0, 1.0, 0.0]

    def test_signal_features_fire(self):
        with_sig = extract_features(
            source="zap", rule_id="r", title="injection", description="payload reflected"
        )
        without = extract_features(source="zap", rule_id="r", title="ok", description="fine")
        assert with_sig[8:].count(1.0) > without[8:].count(1.0)

    def test_deterministic(self):
        kwargs = dict(source="zap", rule_id="40018", title="t", description="d", param="p")
        assert extract_features(**kwargs) == extract_features(**kwargs)


class TestRulesFallback:
    def test_known_rules(self):
        assert _rule_based("zap", "40018", "") == Severity.HIGH
        assert _rule_based("zap", "40014", "") == Severity.CRITICAL

    def test_source_defaults(self):
        assert _rule_based("sqlmap", "unknown", "") == Severity.CRITICAL
        assert _rule_based("xss_engine", "unknown", "") == Severity.HIGH
        assert _rule_based("zap", "unknown", "") == Severity.MEDIUM

    def test_escalators(self):
        # LOW band + "cookie" signal → MEDIUM
        assert _rule_based("zap", "10017", "cookie without samesite") == Severity.MEDIUM

    def test_classifier_without_model_uses_rules(self):
        clf = SeverityClassifier()
        result = clf.classify(source="sqlmap", rule_id="SQLI-CONFIRMED", title="injection")
        assert result.severity == Severity.CRITICAL
        assert result.model == "rules-v0"
        assert 0 < result.confidence <= 1


class TestTrainedArtifact:
    def test_train_load_classify(self, tmp_path, monkeypatch):
        from ml.train import train

        artifact_path = tmp_path / "severity_model.joblib"
        artifact = train(str(artifact_path), n_per_template=12)
        assert artifact["macro_f1"] > 0.5
        assert artifact["train_rows"] > 100
        assert artifact_path.exists()

        monkeypatch.setattr(settings, "ML_MODEL_PATH", str(artifact_path))
        clf = SeverityClassifier()
        assert clf.model_version == "rules-v0"  # before load
        import asyncio

        asyncio.run(clf.load())
        assert clf.model_version == "logreg-calibrated-v1"

        result = clf.classify(
            source="sqlmap",
            rule_id="SQLI-CONFIRMED",
            title="SQL injection in parameter 'id'",
            description="confirmed via sqlmap union technique",
            param="id",
            method="GET",
        )
        assert result.severity in (Severity.HIGH, Severity.CRITICAL)
        assert result.model == "logreg-calibrated-v1"
        assert 0.0 <= result.confidence <= 1.0

    def test_missing_artifact_stays_on_rules(self, tmp_path, monkeypatch):
        monkeypatch.setattr(settings, "ML_MODEL_PATH", str(tmp_path / "nope.joblib"))
        clf = SeverityClassifier()
        import asyncio

        asyncio.run(clf.load())
        assert clf._model is None
        assert clf.model_version == "rules-v0"
