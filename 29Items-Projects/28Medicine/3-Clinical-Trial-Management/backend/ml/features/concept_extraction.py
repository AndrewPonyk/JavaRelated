"""Clinical concept extraction from free-text notes.

A deterministic, dependency-light implementation: de-identification, negation-aware
keyword detection, and numeric lab-value extraction. This is a real, testable
baseline that fills the ``EligibilityScreener`` interface; a production system
would swap these functions for a clinical NLP model (medspaCy / scispaCy /
fine-tuned transformer) without changing callers.
"""
from __future__ import annotations

import re
from dataclasses import dataclass

# ── De-identification (HIPAA Safe Harbor, simplified) ─────────────────────
_DEID_PATTERNS: list[tuple[re.Pattern[str], str]] = [
    (re.compile(r"\b\d{3}-\d{2}-\d{4}\b"), "[SSN]"),
    (re.compile(r"\bMRN[:\s#]*\d+\b", re.I), "[MRN]"),
    (re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+"), "[EMAIL]"),
    (re.compile(r"\b\d{4}-\d{2}-\d{2}\b"), "[DATE]"),
    (re.compile(r"\b\d{1,2}/\d{1,2}/\d{2,4}\b"), "[DATE]"),
    (re.compile(r"\b(?:Mr|Mrs|Ms|Dr)\.?\s+[A-Z][a-z]+\b"), "[NAME]"),
]

# Negation cues that flip a found concept to "absent".
_NEGATIONS = ("no ", "denies ", "without ", "negative for ", "absence of ", "rules out ", "r/o ")


def deidentify(text: str) -> str:
    """Strip common direct identifiers before any downstream processing."""
    for pattern, repl in _DEID_PATTERNS:
        text = pattern.sub(repl, text)
    return text


@dataclass(frozen=True)
class KeywordHit:
    present: bool
    negated: bool
    evidence: str | None


def find_keyword(text: str, keywords: list[str]) -> KeywordHit:
    """Detect any keyword with simple preceding-negation handling."""
    lowered = text.lower()
    for kw in keywords:
        idx = lowered.find(kw.lower())
        while idx != -1:
            window_start = max(0, idx - 25)
            preceding = lowered[window_start:idx]
            negated = any(neg in preceding for neg in _NEGATIONS)
            evidence = text[window_start : idx + len(kw) + 10].strip()
            if not negated:
                return KeywordHit(present=True, negated=False, evidence=evidence)
            # keep searching for a non-negated mention
            idx = lowered.find(kw.lower(), idx + len(kw))
    # Re-scan to report a negated hit if that's all we found.
    for kw in keywords:
        idx = lowered.find(kw.lower())
        if idx != -1:
            window_start = max(0, idx - 25)
            return KeywordHit(
                present=False, negated=True, evidence=text[window_start : idx + len(kw)].strip()
            )
    return KeywordHit(present=False, negated=False, evidence=None)


def extract_lab(text: str, lab: str) -> float | None:
    """Extract the first numeric value associated with a lab name, e.g. 'HbA1c 8.2%'."""
    pattern = re.compile(rf"{re.escape(lab)}\D{{0,15}}?(\d+(?:\.\d+)?)", re.IGNORECASE)
    match = pattern.search(text)
    return float(match.group(1)) if match else None
