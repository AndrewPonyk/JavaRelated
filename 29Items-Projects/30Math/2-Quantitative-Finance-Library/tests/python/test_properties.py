"""Property-based tests (Hypothesis): numerical invariants over random inputs.

These fuzz the C++ boundary with the whole sensible parameter space, catching
edge cases example-based tests miss.
"""

import numpy as np
import pytest
from hypothesis import given, settings
from hypothesis import strategies as st

from quantfinlib.options import black_scholes as bs

spots = st.floats(min_value=1.0, max_value=1e4)
strikes = st.floats(min_value=1.0, max_value=1e4)
vols = st.floats(min_value=1e-3, max_value=3.0)
rates = st.floats(min_value=-0.05, max_value=0.5)
expiries = st.floats(min_value=1e-3, max_value=30.0)

COMMON = dict(max_examples=100, deadline=None)


@settings(**COMMON)
@given(spot=spots, strike=strikes, vol=vols, rate=rates, expiry=expiries)
def test_put_call_parity_everywhere(spot, strike, vol, rate, expiry):
    call = bs.price(spot, strike, vol, rate, expiry, "call")
    put = bs.price(spot, strike, vol, rate, expiry, "put")
    parity = spot - strike * np.exp(-rate * expiry)
    assert call - put == pytest.approx(parity, abs=1e-8 * max(1.0, spot, strike))


@settings(**COMMON)
@given(spot=spots, strike=strikes, vol=vols, rate=rates, expiry=expiries)
def test_price_is_finite_and_bounded(spot, strike, vol, rate, expiry):
    call = bs.price(spot, strike, vol, rate, expiry, "call")
    assert np.isfinite(call)
    assert -1e-12 <= call <= spot  # a call is never worth more than the underlying
    intrinsic = max(spot - strike * np.exp(-rate * expiry), 0.0)
    assert call >= intrinsic - 1e-8 * max(1.0, spot)


@settings(**COMMON)
@given(spot=spots, strike=strikes, vol=vols, rate=rates, expiry=expiries)
def test_delta_bounds(spot, strike, vol, rate, expiry):
    g = bs.greeks(spot, strike, vol, rate, expiry, "call")
    assert -1e-12 <= g["delta"] <= 1.0 + 1e-12
    assert g["gamma"] >= 0.0
    assert g["vega"] >= 0.0


@settings(max_examples=50, deadline=None)
@given(
    spot=st.floats(min_value=10.0, max_value=1000.0),
    moneyness=st.floats(min_value=0.5, max_value=2.0),
    vol=st.floats(min_value=0.05, max_value=1.5),
    rate=st.floats(min_value=0.0, max_value=0.2),
    expiry=st.floats(min_value=0.05, max_value=5.0),
)
def test_implied_vol_round_trips(spot, moneyness, vol, rate, expiry):
    strike = spot * moneyness
    price = bs.price(spot, strike, vol, rate, expiry, "call")
    if price < 1e-10 * spot:  # numerically zero premium carries no vol information
        return
    recovered = bs.implied_vol(price, spot, strike, rate, expiry, "call")
    # Price-space round trip always holds; vol-space recovery is only
    # well-posed when vega is non-negligible (deep ITM/OTM: vega -> 0).
    assert bs.price(spot, strike, recovered, rate, expiry, "call") == pytest.approx(
        price, abs=1e-8 * max(1.0, price)
    )
    if bs.greeks(spot, strike, vol, rate, expiry, "call")["vega"] > 1e-3 * spot:
        assert recovered == pytest.approx(vol, rel=1e-4, abs=1e-6)
