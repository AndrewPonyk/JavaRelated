"""ML eligibility pipeline tests (pure unit — no Django DB)."""
from __future__ import annotations

from ml.features.concept_extraction import deidentify, extract_lab, find_keyword
from ml.pipelines.eligibility_screening import (
    ELIGIBLE,
    INELIGIBLE,
    UNDETERMINED,
    EligibilityScreener,
    ScreeningInput,
)


def test_deidentify_strips_identifiers():
    text = "Pt SSN 123-45-6789, MRN: 998877, email a@b.com seen 2021-04-05."
    out = deidentify(text)
    assert "123-45-6789" not in out
    assert "998877" not in out
    assert "a@b.com" not in out
    assert "2021-04-05" not in out


def test_find_keyword_handles_negation():
    assert find_keyword("history of diabetes", ["diabetes"]).present is True
    assert find_keyword("denies diabetes", ["diabetes"]).present is False
    assert find_keyword("no chest pain", ["chest pain"]).present is False


def test_extract_lab_reads_value():
    assert extract_lab("HbA1c 8.2% today", "HbA1c") == 8.2
    assert extract_lab("no value here", "HbA1c") is None


def _criteria():
    return [
        {
            "id": "1",
            "type": "INCLUSION",
            "text": "Has diabetes",
            "coded_rule": {"keywords": ["diabetes"], "comparator": "present"},
        },
        {
            "id": "2",
            "type": "EXCLUSION",
            "text": "Pregnant",
            "coded_rule": {"keywords": ["pregnant", "pregnancy"], "comparator": "present"},
        },
    ]


def test_screener_recommends_eligible():
    screener = EligibilityScreener()
    result = screener.run(ScreeningInput("Type 2 diabetes. Denies pregnancy.", _criteria()))
    assert result.recommendation == ELIGIBLE
    assert result.score == 1.0
    assert result.model_version


def test_screener_recommends_ineligible_on_exclusion():
    screener = EligibilityScreener()
    result = screener.run(ScreeningInput("Diabetes. Patient is pregnant.", _criteria()))
    assert result.recommendation == INELIGIBLE


def test_screener_undetermined_when_inclusion_unknown():
    screener = EligibilityScreener()
    result = screener.run(ScreeningInput("No relevant history documented.", _criteria()))
    assert result.recommendation == UNDETERMINED


def test_lab_criterion_evaluated():
    screener = EligibilityScreener()
    criteria = [
        {
            "id": "1",
            "type": "INCLUSION",
            "text": "HbA1c >= 7",
            "coded_rule": {"lab": "HbA1c", "op": ">=", "value": 7.0},
        },
    ]
    assert screener.run(ScreeningInput("HbA1c 8.5%", criteria)).recommendation == ELIGIBLE
    assert screener.run(ScreeningInput("HbA1c 5.0%", criteria)).recommendation == UNDETERMINED
    # 5.0 < 7 → inclusion not satisfied → not eligible
