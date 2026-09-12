"""
Sentiment analysis model wrapper.

Loads a HuggingFace transformer pipeline lazily (first request) so service
startup stays fast and the model is only paid for when used. Falls back to a
tiny lexicon heuristic when transformers/torch are unavailable (e.g. CI), so the
service and its contract remain testable without the heavy dependency.
"""
from __future__ import annotations

import logging
import os
from dataclasses import dataclass

logger = logging.getLogger(__name__)

_DEFAULT_MODEL = os.getenv(
    "ML_MODEL_NAME", "distilbert-base-uncased-finetuned-sst-2-english"
)

# Minimal fallback lexicon for environments without the model.
_POSITIVE = {"good", "great", "excellent", "love", "perfect", "amazing", "best"}
_NEGATIVE = {"bad", "terrible", "awful", "hate", "broken", "worst", "poor"}


@dataclass(frozen=True)
class SentimentResult:
    """Outcome of scoring one piece of text."""

    label: str  # "POSITIVE" | "NEGATIVE" | "NEUTRAL"
    score: float  # confidence in [0, 1]


class SentimentAnalyzer:
    """Thread-safe-enough singleton wrapper around the sentiment model."""

    def __init__(self, model_name: str = _DEFAULT_MODEL) -> None:
        self._model_name = model_name
        self._pipeline = None  # lazily initialised

    def _ensure_pipeline(self) -> None:
        if self._pipeline is not None:
            return
        try:
            from transformers import pipeline  # heavy import, deferred

            logger.info("Loading sentiment model: %s", self._model_name)
            self._pipeline = pipeline("sentiment-analysis", model=self._model_name)
        except Exception as exc:  # noqa: BLE001 - degrade gracefully
            logger.warning("Transformer model unavailable (%s); using heuristic.", exc)
            self._pipeline = "heuristic"

    def analyze(self, text: str) -> SentimentResult:
        """Return the sentiment label and confidence for ``text``."""
        if not text or not text.strip():
            return SentimentResult(label="NEUTRAL", score=0.0)

        self._ensure_pipeline()
        if self._pipeline == "heuristic":
            return self._heuristic(text)

        result = self._pipeline(text[:512])[0]  # truncate to model max length
        return SentimentResult(label=result["label"], score=float(result["score"]))

    @staticmethod
    def _heuristic(text: str) -> SentimentResult:
        tokens = {t.strip(".,!?").lower() for t in text.split()}
        pos = len(tokens & _POSITIVE)
        neg = len(tokens & _NEGATIVE)
        if pos == neg:
            return SentimentResult(label="NEUTRAL", score=0.5)
        label = "POSITIVE" if pos > neg else "NEGATIVE"
        score = (max(pos, neg)) / (pos + neg)
        return SentimentResult(label=label, score=round(score, 3))


# Module-level singleton reused across requests.
analyzer = SentimentAnalyzer()
