"""Direct tests of the pure-Python fallback backend (quantfinlib._pure).

The parity suites compare it against the native core on deterministic
functions; these tests exercise the paths a fallback-only install (sdist,
no compiler) would run — especially the Monte Carlo engines, whose RNG
streams differ from the native ones by design.
"""

import numpy as np
import pytest

from quantfinlib import _pure

PARAMS = dict(spot=100.0, strike=100.0, vol=0.2, rate=0.05, expiry=1.0)


class TestPureMcEuropean:
    def test_converges_to_closed_form(self):
        res = _pure.mc_price_european(**PARAMS, kind="call", n_paths=200_000, seed=7)
        closed = _pure.bs_price(**PARAMS, kind="call")
        assert abs(res["price"] - closed) < 3 * res["std_error"]

    def test_deterministic_and_antithetic(self):
        a = _pure.mc_price_european(**PARAMS, kind="put", n_paths=50_000, seed=9)
        b = _pure.mc_price_european(**PARAMS, kind="put", n_paths=50_000, seed=9)
        assert a == b
        plain = _pure.mc_price_european(
            **PARAMS, kind="call", n_paths=50_000, seed=9, antithetic=False
        )
        anti = _pure.mc_price_european(
            **PARAMS, kind="call", n_paths=50_000, seed=9, antithetic=True
        )
        assert anti["std_error"] < plain["std_error"]

    def test_pathwise_greeks(self):
        res = _pure.mc_price_european(**PARAMS, kind="call", n_paths=300_000, seed=3)
        greeks = _pure.bs_greeks(**PARAMS, kind="call")
        assert res["delta"] == pytest.approx(greeks["delta"], abs=1e-2)
        assert res["vega"] == pytest.approx(greeks["vega"], rel=3e-2)


class TestPureMcPathDependent:
    def test_asian_cheaper_than_european(self):
        asian = _pure.mc_price_asian(**PARAMS, kind="call", n_steps=50, n_paths=50_000, seed=5)
        assert asian["price"] < _pure.bs_price(**PARAMS, kind="call")

    def test_barrier_in_out_parity_per_path(self):
        common = dict(**PARAMS, kind="call", n_steps=50, n_paths=20_000, seed=21)
        ko = _pure.mc_price_barrier(**common, barrier=130.0, barrier_type="up-out")
        ki = _pure.mc_price_barrier(**common, barrier=130.0, barrier_type="up-in")
        vanilla = _pure.mc_price_barrier(**common, barrier=1e12, barrier_type="up-out")
        assert ko["price"] + ki["price"] == pytest.approx(vanilla["price"], abs=1e-10)

    def test_down_and_out_put(self):
        do = _pure.mc_price_barrier(
            **PARAMS,
            kind="put",
            barrier=70.0,
            barrier_type="down-out",
            n_steps=50,
            n_paths=20_000,
            seed=2,
        )
        assert 0.0 < do["price"] < _pure.bs_price(**PARAMS, kind="put")

    def test_zero_expiry_asian_is_intrinsic(self):
        res = _pure.mc_price_asian(
            spot=110,
            strike=100,
            vol=0.2,
            rate=0.05,
            expiry=0.0,
            kind="call",
            n_steps=10,
            n_paths=100,
            seed=1,
        )
        assert res == {"price": 10.0, "std_error": 0.0, "n_paths": 100}

    def test_input_guards(self):
        with pytest.raises(ValueError):
            _pure.mc_price_asian(**PARAMS, kind="call", n_steps=0)
        with pytest.raises(ValueError):
            _pure.mc_price_barrier(
                **PARAMS, kind="call", barrier=100.0, barrier_type="diagonal-out"
            )
        with pytest.raises(ValueError):
            _pure.mc_price_european(**PARAMS, kind="call", n_paths=0)


class TestPureScalarFunctions:
    def test_implied_vol_round_trip(self):
        price = _pure.bs_price(100, 105, 0.27, 0.03, 0.75, "call")
        assert _pure.bs_implied_vol(price, 100, 105, 0.03, 0.75, "call") == pytest.approx(
            0.27, abs=1e-8
        )

    def test_implied_vol_guards(self):
        with pytest.raises(ValueError):
            _pure.bs_implied_vol(1e-9, 100, 100, 0.05, 1.0, "call")
        with pytest.raises(ValueError):
            _pure.bs_implied_vol(5.0, 100, 100, 0.05, 0.0, "call")

    def test_binomial_guards_and_degenerates(self):
        with pytest.raises(ValueError):
            _pure.binomial_american(100, 100, 0.2, 0.05, 1.0, "call", n_steps=0)
        assert _pure.binomial_american(90, 100, 0.0, 0.05, 1.0, "put") == pytest.approx(10.0)
        assert _pure.binomial_american(110, 100, 0.2, 0.05, 0.0, "call") == pytest.approx(10.0)

    def test_batch_shape_guard(self):
        with pytest.raises(ValueError):
            _pure.bs_price_batch(np.ones(3), np.ones(2), np.ones(3), np.ones(3), np.ones(3), "call")
        with pytest.raises(ValueError):
            _pure.bs_greeks_batch(
                np.ones(3), np.ones(2), np.ones(3), np.ones(3), np.ones(3), "call"
            )
