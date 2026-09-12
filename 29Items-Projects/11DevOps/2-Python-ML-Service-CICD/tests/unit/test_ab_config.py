"""Unit tests for the runtime A/B configuration store."""

from __future__ import annotations

import pytest

from fraud_detection.services.ab_config import ABConfigStore

pytestmark = pytest.mark.unit


def test_seeds_from_settings(settings) -> None:
    store = ABConfigStore()
    assert store.get() == (True, 10)


def test_partial_update_keeps_other_field(settings) -> None:
    store = ABConfigStore()
    assert store.update(traffic_split=55) == (True, 55)
    assert store.update(enabled=False) == (False, 55)
    assert store.get() == (False, 55)


def test_invalid_split_rejected(settings) -> None:
    store = ABConfigStore()
    with pytest.raises(ValueError):
        store.update(traffic_split=101)
    with pytest.raises(ValueError):
        store.update(traffic_split=-1)
    assert store.get() == (True, 10)


def test_reset_reseeds_from_settings(settings) -> None:
    store = ABConfigStore()
    store.update(enabled=False, traffic_split=90)
    store.reset()
    assert store.get() == (True, 10)
