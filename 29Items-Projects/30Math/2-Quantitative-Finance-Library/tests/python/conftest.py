"""Shared fixtures for unit tests."""

import numpy as np
import pytest


@pytest.fixture
def rng() -> np.random.Generator:
    return np.random.default_rng(12345)


@pytest.fixture
def daily_returns(rng) -> np.ndarray:
    """1000 synthetic daily returns, ~N(0, 1%)."""
    return rng.normal(0.0002, 0.01, size=1000)
