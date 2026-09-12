"""Unit tests for the rules-based severity baseline."""

from __future__ import annotations

from app.models.finding import Severity
from app.services.ml_classifier import classifier


def test_sqlmap_confirmed_defaults_critical() -> None:
    result = classifier.classify(source="sqlmap", rule_id="SQLI-CONFIRMED", title="injection")
    assert result.severity == Severity.CRITICAL
    assert result.model == "rules-v0"


def test_zap_sqli_rule_maps_high() -> None:
    result = classifier.classify(source="zap", rule_id="40018", title="SQL Injection")
    assert result.severity == Severity.HIGH


def test_zap_unknown_rule_defaults_medium() -> None:
    result = classifier.classify(source="zap", rule_id="99999", title="Some finding")
    assert result.severity == Severity.MEDIUM


def test_escalation_signal_bumps_band() -> None:
    # medium + "session" in description → escalated to high
    result = classifier.classify(
        source="zap", rule_id="99999", title="Reflected", description="leaks session id"
    )
    assert result.severity == Severity.HIGH
