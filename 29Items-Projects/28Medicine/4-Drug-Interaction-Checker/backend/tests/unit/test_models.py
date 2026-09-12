"""Tests for Pydantic domain models and validators."""

import pytest
from pydantic import ValidationError

from app.models.common import SEVERITY_ORDER, Severity
from app.models.drug import DrugInput


def test_drug_input_requires_at_least_one_field():
    with pytest.raises(ValidationError):
        DrugInput()


def test_drug_input_label_priority():
    assert DrugInput(name="aspirin").label() == "aspirin"
    assert DrugInput(rxcui="1191").label() == "1191"
    assert DrugInput(ndc="0093-0123").label() == "0093-0123"


def test_drug_input_label_prefers_name():
    assert DrugInput(rxcui="1", name="aspirin").label() == "aspirin"


def test_severity_order_is_monotonic():
    assert (
        SEVERITY_ORDER[Severity.UNKNOWN]
        < SEVERITY_ORDER[Severity.MINOR]
        < SEVERITY_ORDER[Severity.MODERATE]
        < SEVERITY_ORDER[Severity.MAJOR]
        < SEVERITY_ORDER[Severity.CONTRAINDICATED]
    )
