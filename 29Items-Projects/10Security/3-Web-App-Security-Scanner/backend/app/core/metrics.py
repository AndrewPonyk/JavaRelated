"""Hand-rolled Prometheus text-format metrics (no client dependency).

Counters and timing histograms for: HTTP traffic, scans, findings by
severity, scanner phase latencies, auth events. Exposed at /metrics.
"""

from __future__ import annotations

import threading
import time
from collections import defaultdict

_lock = threading.Lock()
_counters: dict[str, dict[str, float]] = defaultdict(lambda: defaultdict(float))
_histograms: dict[str, dict[str, list[float]]] = defaultdict(lambda: defaultdict(list))

_FINDING_SEVERITIES = ("info", "low", "medium", "high", "critical")
_SCANNER_PHASES = ("zap_spider", "zap_active", "sqlmap", "xss")


def count(name: str, value: float = 1, *, labels: dict[str, str] | None = None) -> None:
    key = ",".join(f'{k}="{v}"' for k, v in sorted((labels or {}).items()))
    with _lock:
        _counters[name][key] += value


def observe(name: str, seconds: float, *, labels: dict[str, str] | None = None) -> None:
    key = ",".join(f'{k}="{v}"' for k, v in sorted((labels or {}).items()))
    with _lock:
        _histograms[name][key].append(seconds)


class _Timer:
    """Context manager recording wall-clock duration into a histogram."""

    def __init__(self, name: str, **labels: str) -> None:
        self._name = name
        self._labels = labels

    def __enter__(self) -> _Timer:
        self._start = time.perf_counter()
        return self

    def __exit__(self, *exc) -> None:
        observe(self._name, time.perf_counter() - self._start, labels=self._labels)


def timer(name: str, **labels: str) -> _Timer:
    return _Timer(name, **labels)


def render() -> str:
    """Render the registry as Prometheus text exposition format."""
    lines: list[str] = []
    with _lock:
        for name, series in _counters.items():
            lines.append(f"# TYPE {name} counter")
            for labels, value in sorted(series.items()):
                suffix = f"{{{labels}}}" if labels else ""
                lines.append(f"{name}{suffix} {value:g}")
        for name, histogram in _histograms.items():
            lines.append(f"# TYPE {name} summary")
            for labels, values in sorted(histogram.items()):
                base_labels = f"{labels}," if labels else ""
                for quantile in (0.5, 0.9, 0.99):
                    ordered = sorted(values)
                    idx = min(int(quantile * len(ordered)), len(ordered) - 1)
                    lines.append(f'{name}{{{base_labels}quantile="{quantile}"}} {ordered[idx]:g}')
                suffix = f"{{{labels}}}" if labels else ""
                lines.append(f"{name}_count{suffix} {len(values)}")
                lines.append(f"{name}_sum{suffix} {sum(values):g}")
    # Pre-register the series that matter for dashboards even when zero
    for sev in _FINDING_SEVERITIES:
        if f'{{severity="{sev}"}}' not in _counters.get("findings_total", {}):
            lines.append(f'findings_total{{severity="{sev}"}} 0')
    for phase in _SCANNER_PHASES:
        if f'{{phase="{phase}"}}' not in _histograms.get("scanner_phase_seconds", {}):
            lines.append(f'scanner_phase_seconds_count{{phase="{phase}"}} 0')
    return "\n".join(lines) + "\n"


class Metrics:
    """Singleton facade — the importable `metrics` object used across the app."""

    count = staticmethod(count)
    observe = staticmethod(observe)
    timer = staticmethod(timer)
    render = staticmethod(render)


metrics = Metrics()
