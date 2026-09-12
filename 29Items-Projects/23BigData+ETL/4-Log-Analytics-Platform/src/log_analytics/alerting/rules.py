"""Threshold alert rules: model, YAML loader, pure evaluator.

Consumed by the pattern-detection Spark job (compiles rules to DataFrame filters) and by
the alerting engine (evaluates anomaly_score rules in-process). Pure Python, fully unit-tested.
"""

from __future__ import annotations

import operator
import re
from collections.abc import Callable, Mapping
from dataclasses import dataclass
from datetime import timedelta
from pathlib import Path
from typing import Any

from log_analytics.common.models import AlertSeverity

OPS: dict[str, Callable[[float, float], bool]] = {
    "gt": operator.gt,
    "gte": operator.ge,
    "lt": operator.lt,
    "lte": operator.le,
}

_WINDOW_RE = re.compile(r"^(\d+)([smh])$")
_WINDOW_UNITS = {"s": "seconds", "m": "minutes", "h": "hours"}


def parse_window(window: str) -> timedelta:
    """'30s' / '5m' / '1h' → timedelta. Raises ValueError on anything else."""
    match = _WINDOW_RE.match(window.strip())
    if not match:
        raise ValueError(f"invalid window {window!r} — expected forms like 30s, 5m, 1h")
    value, unit = match.groups()
    return timedelta(**{_WINDOW_UNITS[unit]: int(value)})


@dataclass(frozen=True)
class ThresholdRule:
    id: str
    name: str
    metric: str
    op: str
    threshold: float
    window: str = "5m"
    severity: AlertSeverity = AlertSeverity.WARNING
    channels: tuple[str, ...] = ("log",)
    enabled: bool = True
    description: str = ""

    def __post_init__(self) -> None:
        if self.op not in OPS:
            raise ValueError(f"rule {self.id!r}: unknown operator {self.op!r}")
        parse_window(self.window)  # validate shape early, not at evaluation time

    @property
    def window_delta(self) -> timedelta:
        return parse_window(self.window)


def evaluate(rule: ThresholdRule, metrics: Mapping[str, float]) -> bool:
    """True if the rule fires for this snapshot. Missing metric → no fire (no data ≠ breach)."""
    if not rule.enabled:
        return False
    value = metrics.get(rule.metric)
    if value is None:
        return False
    return OPS[rule.op](float(value), rule.threshold)


def load_rules(path: str | Path) -> list[ThresholdRule]:
    """Load and validate rules from YAML (see config/alert_rules.yaml)."""
    import yaml  # lazy: keep this module importable in minimal environments

    raw: dict[str, Any] = yaml.safe_load(Path(path).read_text(encoding="utf-8")) or {}
    entries = raw.get("rules", [])
    rules: list[ThresholdRule] = []
    seen: set[str] = set()
    for entry in entries:
        rule = ThresholdRule(
            id=str(entry["id"]),
            name=str(entry.get("name", entry["id"])),
            metric=str(entry["metric"]),
            op=str(entry.get("op", "gt")),
            threshold=float(entry["threshold"]),
            window=str(entry.get("window", "5m")),
            severity=AlertSeverity(entry.get("severity", "warning")),
            channels=tuple(entry.get("channels", ["log"])),
            enabled=bool(entry.get("enabled", True)),
            description=str(entry.get("description", "")),
        )
        if rule.id in seen:
            raise ValueError(f"duplicate rule id {rule.id!r} in {path}")
        seen.add(rule.id)
        rules.append(rule)
    return rules
