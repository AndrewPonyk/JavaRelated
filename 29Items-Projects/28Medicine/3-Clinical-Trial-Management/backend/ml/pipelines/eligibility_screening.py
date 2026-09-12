"""NLP eligibility screening pipeline (portable, framework-agnostic).

Deliberately **outside** ``apps/`` so it has no Django dependency and can later be
promoted to a SageMaker endpoint without rewrites (ARCHITECTURE §2.1).
``apps.eligibility.tasks`` is the only Django-facing caller.

Stages:
    1. De-identify the note (HIPAA Safe Harbor) BEFORE any processing.
    2. Evaluate each inclusion/exclusion criterion against the note.
    3. Produce an advisory score + recommendation + per-criterion rationale.

The matcher is rule-based but real: it interprets each criterion's ``coded_rule``
(keyword presence/absence or a numeric lab comparison) and records the evidence
span it relied on. Eligibility logic: all INCLUSION criteria must be satisfied and
no EXCLUSION criterion may be satisfied.
"""
from __future__ import annotations

import operator
from dataclasses import dataclass, field
from typing import Any

from ml.features.concept_extraction import deidentify, extract_lab, find_keyword

# Decision strings mirror apps.eligibility.models.Decision (kept decoupled here).
ELIGIBLE = "ELIGIBLE"
INELIGIBLE = "INELIGIBLE"
UNDETERMINED = "UNDETERMINED"

_OPS = {">=": operator.ge, ">": operator.gt, "<=": operator.le, "<": operator.lt, "==": operator.eq}
MODEL_VERSION = "rule-based-1.0.0"
ONTOLOGY_VERSIONS = {"SNOMEDCT": "2024-03", "ICD10": "2024", "RxNorm": "2024-03", "LOINC": "2.77"}


@dataclass(frozen=True)
class ScreeningInput:
    note_text: str
    criteria: list[dict[str, Any]]  # each: {type, text, coded_rule, id?}


@dataclass(frozen=True)
class EligibilityResult:
    score: float
    recommendation: str
    rationale: list[dict[str, Any]] = field(default_factory=list)
    model_version: str = MODEL_VERSION
    ontology_versions: dict[str, str] = field(default_factory=lambda: dict(ONTOLOGY_VERSIONS))


class EligibilityScreener:
    """Advisory eligibility screener. Never the authoritative decision-maker."""

    def __init__(self, backend: str = "local", min_confidence: float = 0.70) -> None:
        self.backend = backend
        self.min_confidence = min_confidence

    @classmethod
    def from_settings(cls, settings) -> EligibilityScreener:
        return cls(
            backend=getattr(settings, "ML_BACKEND", "local"),
            min_confidence=getattr(settings, "ELIGIBILITY_MIN_CONFIDENCE", 0.70),
        )

    def run(self, data: ScreeningInput) -> EligibilityResult:
        note = deidentify(data.note_text or "")
        rationale: list[dict[str, Any]] = []
        favorable = 0
        has_exclusion_hit = False
        unmet_inclusion = False

        for crit in data.criteria:
            ctype = (crit.get("type") or "INCLUSION").upper()
            satisfied, evidence = self._evaluate(note, crit.get("coded_rule") or {})
            # "satisfied" == the criterion's condition is TRUE for this patient.
            if ctype == "INCLUSION":
                favorable += int(satisfied is True)
                if satisfied is not True:
                    unmet_inclusion = True
            else:  # EXCLUSION
                favorable += int(satisfied is False)
                if satisfied is True:
                    has_exclusion_hit = True
            rationale.append(
                {
                    "criterion_id": crit.get("id"),
                    "type": ctype,
                    "text": crit.get("text", ""),
                    "satisfied": satisfied,
                    "evidence": evidence,
                }
            )

        total = len(data.criteria)
        score = round(favorable / total, 3) if total else 0.0

        if has_exclusion_hit:
            recommendation = INELIGIBLE
        elif total and not unmet_inclusion:
            recommendation = ELIGIBLE
        else:
            recommendation = UNDETERMINED

        return EligibilityResult(score=score, recommendation=recommendation, rationale=rationale)

    # -- criterion evaluation ----------------------------------------------
    def _evaluate(self, note: str, rule: dict[str, Any]) -> tuple[bool | None, str | None]:
        """Return (satisfied, evidence). ``None`` means undetermined."""
        if "keywords" in rule:
            hit = find_keyword(note, list(rule["keywords"]))
            comparator = rule.get("comparator", "present")
            if hit.evidence is None:
                return (None if comparator == "present" else True, None)
            present = hit.present
            satisfied = present if comparator == "present" else (not present)
            return satisfied, hit.evidence
        if "lab" in rule:
            value = extract_lab(note, rule["lab"])
            if value is None:
                return None, None
            op = _OPS.get(rule.get("op", ">="))
            if op is None:  # unrecognized comparator → cannot determine
                return None, None
            threshold = float(rule.get("value", 0))
            return op(value, threshold), f"{rule['lab']}={value}"
        # Unknown/empty rule → cannot determine.
        return None, None
