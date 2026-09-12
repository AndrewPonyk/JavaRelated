"""Unit tests for alerting/rules.py — pure rule model, YAML loading, evaluation."""

from __future__ import annotations

from datetime import timedelta
from pathlib import Path

import pytest

from log_analytics.alerting.rules import OPS, ThresholdRule, evaluate, load_rules, parse_window
from log_analytics.common.models import AlertSeverity


class TestParseWindow:
    @pytest.mark.parametrize(
        ("window", "expected"),
        [("30s", timedelta(seconds=30)), ("5m", timedelta(minutes=5)), ("1h", timedelta(hours=1))],
    )
    def test_valid(self, window: str, expected: timedelta) -> None:
        assert parse_window(window) == expected

    @pytest.mark.parametrize("bad", ["", "5", "m5", "5d", "1.5m", "5 m"])
    def test_invalid_raises(self, bad: str) -> None:
        with pytest.raises(ValueError):
            parse_window(bad)


def _rule(**overrides) -> ThresholdRule:
    defaults = {"id": "r1", "name": "rule", "metric": "error_ratio", "op": "gt", "threshold": 0.05}
    defaults.update(overrides)
    return ThresholdRule(**defaults)


class TestEvaluate:
    def test_fires_above_threshold(self) -> None:
        assert evaluate(_rule(), {"error_ratio": 0.10}) is True

    def test_boundary_is_operator_exact(self) -> None:
        assert evaluate(_rule(op="gt"), {"error_ratio": 0.05}) is False
        assert evaluate(_rule(op="gte"), {"error_ratio": 0.05}) is True

    def test_missing_metric_never_fires(self) -> None:
        assert evaluate(_rule(), {"log_count": 100.0}) is False

    def test_disabled_rule_never_fires(self) -> None:
        assert evaluate(_rule(enabled=False), {"error_ratio": 1.0}) is False

    def test_all_operators_present(self) -> None:
        assert set(OPS) == {"gt", "gte", "lt", "lte"}

    def test_unknown_operator_rejected_at_construction(self) -> None:
        with pytest.raises(ValueError, match="unknown operator"):
            _rule(op="between")

    def test_bad_window_rejected_at_construction(self) -> None:
        with pytest.raises(ValueError):
            _rule(window="5x")


class TestLoadRules:
    def test_loads_the_shipped_ruleset(self) -> None:
        rules = load_rules(Path(__file__).resolve().parents[2] / "config" / "alert_rules.yaml")
        ids = {r.id for r in rules}
        assert {"high-error-ratio", "error-burst"} <= ids
        burst = next(r for r in rules if r.id == "error-burst")
        assert burst.severity is AlertSeverity.CRITICAL
        assert "pagerduty" in burst.channels

    def test_duplicate_ids_rejected(self, tmp_path: Path) -> None:
        yaml_file = tmp_path / "rules.yaml"
        yaml_file.write_text(
            "rules:\n"
            "  - {id: dup, metric: log_count, threshold: 1}\n"
            "  - {id: dup, metric: log_count, threshold: 2}\n",
            encoding="utf-8",
        )
        with pytest.raises(ValueError, match="duplicate rule id"):
            load_rules(yaml_file)

    def test_empty_file_gives_no_rules(self, tmp_path: Path) -> None:
        yaml_file = tmp_path / "rules.yaml"
        yaml_file.write_text("", encoding="utf-8")
        assert load_rules(yaml_file) == []
