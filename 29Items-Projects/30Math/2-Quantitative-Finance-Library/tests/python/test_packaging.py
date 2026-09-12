"""Packaging invariants: version sync, extension contract."""

import re
from pathlib import Path

import tomllib

import quantfinlib

ROOT = Path(__file__).resolve().parents[2]


def test_version_single_source():
    pyproject = tomllib.loads((ROOT / "pyproject.toml").read_text())
    assert quantfinlib.__version__ == pyproject["project"]["version"]
    cmake = (ROOT / "CMakeLists.txt").read_text()
    assert re.search(
        rf"VERSION\s+{re.escape(quantfinlib.__version__)}\b", cmake
    ), "CMake project VERSION out of sync with pyproject.toml"


def test_backend_flag_is_reported():
    assert isinstance(quantfinlib.HAS_NATIVE, bool)


def test_public_api_surface():
    from quantfinlib.options import american, black_scholes, monte_carlo
    from quantfinlib.risk import portfolio_var, value_at_risk  # noqa: F401

    for fn in ("price", "greeks", "implied_vol"):
        assert callable(getattr(black_scholes, fn))
    for fn in ("price_european", "price_asian", "price_barrier"):
        assert callable(getattr(monte_carlo, fn))
    assert callable(american.price_american)
