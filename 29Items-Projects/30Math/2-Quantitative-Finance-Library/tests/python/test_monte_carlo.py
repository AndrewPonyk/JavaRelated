"""MC tests are statistical: fixed seeds + k·standard-error bounds, never
magic absolute tolerances (TECH-NOTES §3.6 #4)."""

import pytest

from quantfinlib.options import black_scholes as bs
from quantfinlib.options import monte_carlo as mc
from quantfinlib.utils.validation import InvalidInputError

PARAMS = dict(spot=100, strike=100, vol=0.2, rate=0.05, expiry=1.0)


class TestEuropean:
    def test_converges_to_closed_form_within_3_se(self):
        result = mc.price_european(**PARAMS, kind="call", n_paths=200_000, seed=7)
        closed = bs.price(**PARAMS, kind="call")
        assert abs(result.price - closed) < 3 * result.std_error

    def test_deterministic_for_fixed_seed(self):
        a = mc.price_european(**PARAMS, kind="put", n_paths=50_000, seed=99)
        b = mc.price_european(**PARAMS, kind="put", n_paths=50_000, seed=99)
        assert a.price == b.price
        assert a.std_error == b.std_error

    def test_antithetic_reduces_variance(self):
        plain = mc.price_european(**PARAMS, kind="call", n_paths=100_000, seed=1, antithetic=False)
        anti = mc.price_european(**PARAMS, kind="call", n_paths=100_000, seed=1, antithetic=True)
        assert anti.std_error < plain.std_error

    def test_pathwise_greeks_match_closed_form(self):
        result = mc.price_european(**PARAMS, kind="call", n_paths=500_000, seed=11)
        greeks = bs.greeks(**PARAMS, kind="call")
        assert result.delta == pytest.approx(greeks["delta"], abs=5e-3)
        assert result.vega == pytest.approx(greeks["vega"], rel=2e-2)

    def test_pathwise_greeks_put(self):
        result = mc.price_european(**PARAMS, kind="put", n_paths=500_000, seed=11)
        greeks = bs.greeks(**PARAMS, kind="put")
        assert result.delta == pytest.approx(greeks["delta"], abs=5e-3)
        assert result.delta < 0

    @pytest.mark.slow
    def test_standard_error_shrinks_like_sqrt_n(self):
        small = mc.price_european(**PARAMS, kind="call", n_paths=10_000, seed=3)
        large = mc.price_european(**PARAMS, kind="call", n_paths=1_000_000, seed=3)
        ratio = small.std_error / large.std_error
        assert ratio == pytest.approx(10.0, rel=0.25)  # sqrt(100) with statistical slack

    def test_n_paths_guard(self):
        with pytest.raises(InvalidInputError):
            mc.price_european(**PARAMS, kind="call", n_paths=0)
        with pytest.raises(InvalidInputError):
            mc.price_european(**PARAMS, kind="call", n_paths=mc.MAX_PATHS + 1)


class TestAsian:
    def test_asian_cheaper_than_european(self):
        # Averaging damps volatility: arithmetic Asian <= European (same terms).
        asian = mc.price_asian(**PARAMS, kind="call", n_steps=50, n_paths=100_000, seed=5)
        european = bs.price(**PARAMS, kind="call")
        assert asian.price < european

    def test_deterministic_for_fixed_seed(self):
        a = mc.price_asian(**PARAMS, kind="call", n_steps=50, n_paths=20_000, seed=13)
        b = mc.price_asian(**PARAMS, kind="call", n_steps=50, n_paths=20_000, seed=13)
        assert a.price == b.price

    def test_step_guard(self):
        with pytest.raises(InvalidInputError):
            mc.price_asian(**PARAMS, kind="call", n_steps=0)


class TestBarrier:
    def test_in_out_parity_is_exact_per_path(self):
        # KO + KI payoffs sum to the vanilla payoff on every path, so with a
        # common seed the identity holds to floating-point rounding, not MC error.
        common = dict(**PARAMS, kind="call", n_steps=50, n_paths=50_000, seed=21)
        ko = mc.price_barrier(barrier=130.0, barrier_type="up-out", **common)
        ki = mc.price_barrier(barrier=130.0, barrier_type="up-in", **common)
        vanilla = mc.price_european(**PARAMS, kind="call", n_paths=50_000, seed=21)
        # Same seed but different path construction (stepped vs terminal), so
        # compare KO+KI against the *stepped* vanilla: barrier at infinity.
        stepped_vanilla = mc.price_barrier(barrier=1e12, barrier_type="up-out", **common)
        assert ko.price + ki.price == pytest.approx(stepped_vanilla.price, abs=1e-10)
        # And statistically the stepped vanilla agrees with the exact one.
        assert abs(stepped_vanilla.price - vanilla.price) < 4 * (
            stepped_vanilla.std_error + vanilla.std_error
        )

    def test_knock_out_cheaper_than_vanilla(self):
        ko = mc.price_barrier(
            **PARAMS,
            kind="call",
            barrier=120.0,
            barrier_type="up-out",
            n_steps=50,
            n_paths=50_000,
            seed=2,
        )
        vanilla = bs.price(**PARAMS, kind="call")
        assert ko.price < vanilla

    def test_born_knocked_out_is_worthless(self):
        ko = mc.price_barrier(
            **PARAMS,
            kind="call",
            barrier=90.0,
            barrier_type="up-out",
            n_steps=10,
            n_paths=1_000,
            seed=2,
        )
        assert ko.price == 0.0

    def test_bad_barrier_type_rejected(self):
        with pytest.raises(InvalidInputError):
            mc.price_barrier(
                **PARAMS, kind="call", barrier=120.0, barrier_type="sideways-out", n_steps=10
            )

    def test_bad_barrier_level_rejected(self):
        with pytest.raises(InvalidInputError):
            mc.price_barrier(**PARAMS, kind="call", barrier=-1.0, barrier_type="up-out", n_steps=10)
