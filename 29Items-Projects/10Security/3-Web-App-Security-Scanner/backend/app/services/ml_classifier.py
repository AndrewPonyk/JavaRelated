"""ML severity classifier — predicts Finding.severity from finding features.

Primary path: calibrated logistic-regression artifact (ml/train.py, loaded
at startup from ML_MODEL_PATH) with real confidence from predict_proba.
Fallback path: deterministic rules baseline (used when the artifact is
missing/unloadable) so classification NEVER blocks ingestion.

Inference stays sync+cheap (≤32 features, linear model). If the model grows
beyond linear, wrap calls in asyncio.to_thread (TECH-NOTES pitfall #5).
"""

from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from app.core.config import settings
from app.models.finding import Severity
from app.services.ml_features import extract_features

logger = logging.getLogger(__name__)


@dataclass
class Classification:
    severity: Severity
    confidence: float  # 0..1
    model: str  # "logreg-calibrated-v1" | "rules-v0"


# ── rules baseline (fallback) ──────────────────────────────────────────

_RULE_BASE: dict[str, Severity] = {
    "40018": Severity.HIGH,  # SQL injection
    "40019": Severity.HIGH,  # OS command injection
    "40014": Severity.CRITICAL,  # buffer overflow
    "10038": Severity.LOW,  # CSP header not set
    "10098": Severity.LOW,  # cross-domain misconfiguration
}
_SOURCE_DEFAULT: dict[str, Severity] = {
    "sqlmap": Severity.CRITICAL,
    "xss_engine": Severity.HIGH,
    "zap": Severity.MEDIUM,
}
_ESCALATORS = {
    Severity.MEDIUM: ("admin", "authenticated", "session", "token"),
    Severity.LOW: ("cookie", "header"),
}
_ORDER = [Severity.INFO, Severity.LOW, Severity.MEDIUM, Severity.HIGH, Severity.CRITICAL]


def _rule_based(source: str, rule_id: str, text: str) -> Severity:
    severity = _RULE_BASE.get(rule_id) or _SOURCE_DEFAULT.get(source, Severity.INFO)
    text_l = text.lower()
    for band, signals in _ESCALATORS.items():
        if severity == band and any(s in text_l for s in signals):
            return _ORDER[min(_ORDER.index(severity) + 1, len(_ORDER) - 1)]
    return severity


# ── classifier ─────────────────────────────────────────────────────────


class SeverityClassifier:
    def __init__(self) -> None:
        self._model = None
        self._meta: dict = {}

    @property
    def model_version(self) -> str:
        return self._meta.get("version", "rules-v0")

    async def load(self) -> None:
        """Load the trained artifact if present; stay on rules otherwise."""
        path = Path(settings.ML_MODEL_PATH)
        if not path.exists():
            logger.info("ML artifact %s not found — using rules baseline", path)
            return
        try:
            import joblib

            def _load() -> Any:
                return joblib.load(path)

            artifact = await asyncio.to_thread(_load)
            if artifact.get("feature_names") and artifact.get("model"):
                self._model = artifact["model"]
                self._meta = artifact
                logger.info(
                    "ML model loaded: %s (macro-F1 %.3f, %s train rows)",
                    artifact["version"],
                    artifact.get("macro_f1", -1),
                    artifact.get("train_rows"),
                )
        except Exception:
            logger.exception("failed to load ML artifact %s — rules baseline active", path)

    def classify(
        self,
        *,
        source: str,
        rule_id: str,
        title: str = "",
        description: str = "",
        param: str | None = None,
        method: str | None = None,
    ) -> Classification:
        if self._model is not None:
            vector = extract_features(
                source=source,
                rule_id=rule_id,
                title=title,
                description=description,
                param=param,
                method=method,
            )
            prediction = self._model.predict([vector])[0]
            proba = self._model.predict_proba([vector])[0]
            classes = list(self._model.classes_)
            try:
                severity = Severity(str(prediction))
            except ValueError:
                severity = Severity(settings.ML_FALLBACK_SEVERITY)
            return Classification(
                severity=severity,
                confidence=float(proba[classes.index(prediction)]),
                model=self._meta.get("version", "logreg"),
            )

        severity = _rule_based(source, rule_id, f"{title} {description}")
        return Classification(severity=severity, confidence=0.6, model="rules-v0")


# Module-level singleton — imported by the orchestrator
classifier = SeverityClassifier()
