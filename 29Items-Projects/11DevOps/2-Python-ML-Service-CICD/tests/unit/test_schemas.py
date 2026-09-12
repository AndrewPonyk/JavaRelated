"""Unit tests for request/response schema validation."""

from __future__ import annotations

import pytest
from pydantic import ValidationError

from fraud_detection.schemas.model import ABConfigUpdate, PromoteRequest
from fraud_detection.schemas.prediction import PredictionRequest

pytestmark = pytest.mark.unit

VALID: dict[str, object] = {
    "transaction_id": "txn-1",
    "account_id": "acct-1",
    "amount": 99.99,
    "merchant_category": "electronics",
    "timestamp": "2026-07-12T10:00:00Z",
}


def test_valid_request_parses_with_extras() -> None:
    request = PredictionRequest(**{**VALID, "features": {"velocity_1h": 3.0}})
    assert request.amount == pytest.approx(99.99)
    assert request.features == {"velocity_1h": 3.0}


def test_negative_and_zero_amounts_rejected() -> None:
    for amount in (-5, 0):
        with pytest.raises(ValidationError):
            PredictionRequest(**{**VALID, "amount": amount})


def test_missing_required_field_rejected() -> None:
    payload = {key: value for key, value in VALID.items() if key != "account_id"}
    with pytest.raises(ValidationError):
        PredictionRequest(**payload)


def test_empty_identifiers_rejected() -> None:
    with pytest.raises(ValidationError):
        PredictionRequest(**{**VALID, "transaction_id": ""})
    with pytest.raises(ValidationError):
        PredictionRequest(**{**VALID, "merchant_category": ""})


def test_ab_config_update_bounds() -> None:
    assert ABConfigUpdate(traffic_split=0).traffic_split == 0
    assert ABConfigUpdate(traffic_split=100).traffic_split == 100
    for bad in (-1, 101):
        with pytest.raises(ValidationError):
            ABConfigUpdate(traffic_split=bad)


def test_promote_request_alias_is_restricted() -> None:
    assert PromoteRequest(version="2").alias == "champion"
    with pytest.raises(ValidationError):
        PromoteRequest(version="2", alias="production")
