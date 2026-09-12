"""Black-Scholes unit tests: golden values, invariants, backend parity."""

import numpy as np
import pytest

from quantfinlib import _pure
from quantfinlib.options import black_scholes as bs
from quantfinlib.utils.validation import InvalidInputError

# Hull reference: S=42, K=40, r=0.10, sigma=0.20, T=0.5
HULL = dict(spot=42, strike=40, vol=0.20, rate=0.10, expiry=0.5)


class TestPrice:
    def test_hull_reference_call(self):
        assert bs.price(**HULL, kind="call") == pytest.approx(4.759, abs=5e-4)

    def test_hull_reference_put(self):
        assert bs.price(**HULL, kind="put") == pytest.approx(0.8086, abs=5e-4)

    def test_put_call_parity(self):
        s, k, v, r, t = 100, 95, 0.3, 0.05, 1.25
        call = bs.price(s, k, v, r, t, "call")
        put = bs.price(s, k, v, r, t, "put")
        assert call - put == pytest.approx(s - k * np.exp(-r * t), abs=1e-12)

    def test_batch_matches_scalar(self):
        strikes = np.array([80.0, 90.0, 100.0, 110.0, 120.0])
        batch = bs.price(100, strikes, 0.2, 0.05, 1.0, "call")
        scalars = [bs.price(100, float(k), 0.2, 0.05, 1.0, "call") for k in strikes]
        np.testing.assert_allclose(batch, scalars, rtol=0, atol=1e-14)

    def test_price_increases_in_vol(self):
        vols = np.linspace(0.05, 1.0, 20)
        prices = np.array([bs.price(100, 100, float(v), 0.05, 1.0, "call") for v in vols])
        assert np.all(np.diff(prices) > 0)

    def test_backend_parity_with_pure_python(self):
        """C++ core and pure-Python reference must agree to 1e-12 (the real
        test of the bindings). Trivially true when running on the fallback."""
        cases = [
            (100, 90, 0.15, 0.02, 0.25),
            (50, 55, 0.45, 0.0, 2.0),
            (100, 100, 0.2, 0.05, 1.0),
            (110, 100, 0.2, 0.05, 0.0),
            (100, 100, 0.0, 0.05, 1.0),
        ]
        for s, k, v, r, t in cases:
            for kind in ("call", "put"):
                assert bs.price(s, k, v, r, t, kind) == pytest.approx(
                    _pure.bs_price(s, k, v, r, t, kind), abs=1e-12
                )


class TestGreeks:
    def test_delta_vs_finite_difference(self):
        s, k, v, r, t = 100.0, 100.0, 0.2, 0.05, 1.0
        g = bs.greeks(s, k, v, r, t, "call")
        h = 1e-4
        fd_delta = (bs.price(s + h, k, v, r, t, "call") - bs.price(s - h, k, v, r, t, "call")) / (
            2 * h
        )
        assert g["delta"] == pytest.approx(fd_delta, rel=1e-6)

    def test_vega_vs_finite_difference(self):
        s, k, v, r, t = 100.0, 110.0, 0.25, 0.03, 0.5
        g = bs.greeks(s, k, v, r, t, "put")
        h = 1e-5
        fd_vega = (bs.price(s, k, v + h, r, t, "put") - bs.price(s, k, v - h, r, t, "put")) / (
            2 * h
        )
        assert g["vega"] == pytest.approx(fd_vega, rel=1e-6)

    def test_gamma_and_theta_vs_finite_difference(self):
        s, k, v, r, t = 95.0, 100.0, 0.3, 0.02, 0.75
        g = bs.greeks(s, k, v, r, t, "call")
        h = 1e-3
        fd_gamma = (
            bs.price(s + h, k, v, r, t, "call")
            - 2 * bs.price(s, k, v, r, t, "call")
            + bs.price(s - h, k, v, r, t, "call")
        ) / h**2
        fd_theta = (
            -(bs.price(s, k, v, r, t + 1e-6, "call") - bs.price(s, k, v, r, t - 1e-6, "call"))
            / 2e-6
        )
        assert g["gamma"] == pytest.approx(fd_gamma, rel=1e-4)
        assert g["theta"] == pytest.approx(fd_theta, rel=1e-4)

    def test_vectorised_greeks_match_scalar(self):
        strikes = np.array([80.0, 100.0, 120.0])
        batch = bs.greeks(100, strikes, 0.2, 0.05, 1.0, "call")
        assert set(batch) == {"delta", "gamma", "vega", "theta", "rho"}
        for i, k in enumerate(strikes):
            scalar = bs.greeks(100, float(k), 0.2, 0.05, 1.0, "call")
            for name, values in batch.items():
                assert values[i] == pytest.approx(scalar[name], abs=1e-12)

    def test_limit_values_at_expiry(self):
        itm = bs.greeks(110, 100, 0.2, 0.05, 0.0, "call")
        otm = bs.greeks(90, 100, 0.2, 0.05, 0.0, "call")
        atm = bs.greeks(100, 100, 0.2, 0.05, 0.0, "call")
        assert (itm["delta"], otm["delta"], atm["delta"]) == (1.0, 0.0, 0.5)
        assert itm["gamma"] == itm["vega"] == 0.0
        put = bs.greeks(90, 100, 0.2, 0.05, 0.0, "put")  # ITM put
        assert put["delta"] == -1.0

    def test_limit_values_at_zero_vol(self):
        g = bs.greeks(100, 90, 0.0, 0.05, 1.0, "call")  # forward ITM
        assert g["delta"] == 1.0
        assert g["gamma"] == g["vega"] == 0.0
        assert g["rho"] == pytest.approx(90 * 1.0 * np.exp(-0.05), abs=1e-12)

    def test_greeks_backend_parity(self):
        strikes = np.linspace(70, 130, 7)
        for kind in ("call", "put"):
            native = bs.greeks(100, strikes, 0.25, 0.03, 0.8, kind)
            pure = _pure.bs_greeks_batch(
                np.full(7, 100.0),
                strikes,
                np.full(7, 0.25),
                np.full(7, 0.03),
                np.full(7, 0.8),
                kind,
            )
            for name in native:
                np.testing.assert_allclose(native[name], pure[name], atol=1e-9)


class TestImpliedVol:
    def test_round_trip(self):
        vol = 0.27
        price = bs.price(100, 105, vol, 0.03, 0.75, "call")
        assert bs.implied_vol(price, 100, 105, 0.03, 0.75, "call") == pytest.approx(vol, abs=1e-8)

    def test_round_trip_extreme_moneyness(self):
        for strike in (40.0, 250.0):
            for vol in (0.05, 0.8):
                price = bs.price(100, strike, vol, 0.02, 0.5, "put")
                if price < 1e-12:  # numerically zero premium carries no vol info
                    continue
                recovered = bs.implied_vol(price, 100, strike, 0.02, 0.5, "put")
                # The meaningful invariant is the PRICE round trip; vol-space
                # recovery is only well-posed when vega is non-negligible
                # (deep ITM: vega ~ 0 makes many vols price identically).
                assert bs.price(100, strike, recovered, 0.02, 0.5, "put") == pytest.approx(
                    price, abs=1e-9 * max(1.0, price)
                )
                if bs.greeks(100, strike, vol, 0.02, 0.5, "put")["vega"] > 1e-4:
                    assert recovered == pytest.approx(vol, abs=1e-6)

    def test_unattainable_price_raises(self):
        with pytest.raises(InvalidInputError):
            bs.implied_vol(0.0001, 100, 100, 0.05, 1.0, "call")  # below intrinsic range

    def test_zero_expiry_raises(self):
        with pytest.raises(InvalidInputError):
            bs.implied_vol(5.0, 100, 100, 0.05, 0.0, "call")


class TestValidation:
    @pytest.mark.parametrize(
        "bad",
        [
            dict(spot=-1),
            dict(strike=0),
            dict(vol=-0.1),
            dict(expiry=-1.0),
            dict(spot=float("nan")),
            dict(vol=float("inf")),
        ],
    )
    def test_bad_scalar_inputs_raise(self, bad):
        params = dict(spot=100, strike=100, vol=0.2, rate=0.05, expiry=1.0) | bad
        with pytest.raises((InvalidInputError, ValueError)):
            bs.price(**params, kind="call")

    def test_nan_in_array_raises_not_propagates(self):
        strikes = np.array([90.0, np.nan, 110.0])
        with pytest.raises(InvalidInputError):
            bs.price(100, strikes, 0.2, 0.05, 1.0, "call")

    def test_bad_kind_raises(self):
        with pytest.raises(InvalidInputError):
            bs.price(100, 100, 0.2, 0.05, 1.0, kind="straddle")
