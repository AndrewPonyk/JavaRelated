"""American binomial (CRR) tests."""

import pytest

from quantfinlib import _pure
from quantfinlib.options import black_scholes as bs
from quantfinlib.options import price_american
from quantfinlib.utils.validation import InvalidInputError

PARAMS = dict(spot=100, strike=100, vol=0.2, rate=0.05, expiry=1.0)


def test_american_call_equals_european_without_dividends():
    # With r >= 0 and no dividends, early exercise of a call is never optimal.
    american = price_american(**PARAMS, kind="call", n_steps=2000)
    european = bs.price(**PARAMS, kind="call")
    assert american == pytest.approx(european, abs=5e-3)


def test_american_put_carries_early_exercise_premium():
    american = price_american(
        spot=100, strike=110, vol=0.2, rate=0.05, expiry=1.0, kind="put", n_steps=2000
    )
    european = bs.price(spot=100, strike=110, vol=0.2, rate=0.05, expiry=1.0, kind="put")
    assert american > european


def test_american_never_below_intrinsic():
    deep_itm_put = price_american(spot=50, strike=100, vol=0.2, rate=0.05, expiry=1.0, kind="put")
    assert deep_itm_put >= 50.0 - 1e-12


def test_lattice_converges_with_steps():
    coarse = price_american(**PARAMS, kind="put", n_steps=64)
    fine = price_american(**PARAMS, kind="put", n_steps=4096)
    finer = price_american(**PARAMS, kind="put", n_steps=8192)
    assert abs(fine - finer) < abs(coarse - finer)


def test_degenerate_cases():
    assert price_american(
        spot=90, strike=100, vol=0.2, rate=0.05, expiry=0.0, kind="put"
    ) == pytest.approx(10.0)
    # vol == 0, ITM put: exercise immediately beats waiting (discounting erodes K).
    assert price_american(
        spot=90, strike=100, vol=0.0, rate=0.05, expiry=1.0, kind="put"
    ) == pytest.approx(10.0)


def test_backend_parity_with_pure_python():
    for kind in ("call", "put"):
        facade = price_american(**PARAMS, kind=kind, n_steps=512)
        pure = _pure.binomial_american(100, 100, 0.2, 0.05, 1.0, kind, n_steps=512)
        assert facade == pytest.approx(pure, abs=1e-9)


def test_input_guards():
    with pytest.raises(InvalidInputError):
        price_american(**PARAMS, kind="straddle")
    with pytest.raises(InvalidInputError):
        price_american(**PARAMS, kind="put", n_steps=0)
