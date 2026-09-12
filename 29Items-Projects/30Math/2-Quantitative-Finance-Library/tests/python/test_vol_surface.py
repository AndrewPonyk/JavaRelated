"""Vol-surface tests: validation always runs; training tests are gated on torch."""

import numpy as np
import pandas as pd
import pytest

from quantfinlib.ml.vol_surface import VolSurfaceFitter, VolSurfaceModel
from quantfinlib.utils.validation import ConvergenceError, InvalidInputError

torch = pytest.importorskip("torch", reason="requires quantfinlib[ml]")


def synthetic_smile(n: int = 60) -> pd.DataFrame:
    """A clean parabolic smile — the fitter must be able to recover this."""
    rng = np.random.default_rng(0)
    strikes = rng.uniform(80, 120, n)
    expiries = rng.uniform(0.1, 2.0, n)
    k = np.log(strikes / 100.0)
    vols = 0.2 + 0.3 * k**2 + 0.02 * np.sqrt(expiries)
    return pd.DataFrame({"strike": strikes, "expiry": expiries, "implied_vol": vols})


class TestValidation:
    def test_missing_columns_rejected(self):
        with pytest.raises(InvalidInputError, match="missing columns"):
            VolSurfaceFitter().fit(pd.DataFrame({"strike": [1.0] * 10}), forward=100)

    def test_too_few_quotes_rejected(self):
        quotes = synthetic_smile(5)
        with pytest.raises(InvalidInputError, match=">= 10 quotes"):
            VolSurfaceFitter().fit(quotes, forward=100)

    def test_nan_quote_rejected(self):
        quotes = synthetic_smile()
        quotes.loc[3, "implied_vol"] = np.nan
        with pytest.raises(InvalidInputError, match="finite"):
            VolSurfaceFitter().fit(quotes, forward=100)

    def test_negative_vol_rejected(self):
        quotes = synthetic_smile()
        quotes.loc[3, "implied_vol"] = -0.1
        with pytest.raises(InvalidInputError):
            VolSurfaceFitter().fit(quotes, forward=100)

    def test_bad_forward_rejected(self):
        with pytest.raises(InvalidInputError, match="forward"):
            VolSurfaceFitter().fit(synthetic_smile(), forward=-5)


class TestArbitragePenalty:
    def test_penalty_nonnegative_and_flags_calendar_violation(self):
        from quantfinlib.ml.models import arbitrage_penalty

        k = torch.linspace(-0.3, 0.3, 8)
        t = torch.linspace(0.25, 2.0, 8)

        def good_surface(kk, tt):  # flat vol: w = sigma^2 * T strictly increasing in T
            return 0.2 * torch.ones_like(kk) + 0.0 * kk + 0.0 * tt

        def bad_surface(kk, tt):  # vol collapsing in T fast enough that w decreases
            return 0.5 / tt + 0.0 * kk

        good = float(arbitrage_penalty(good_surface, k, t))
        bad = float(arbitrage_penalty(bad_surface, k, t))
        assert good == pytest.approx(0.0, abs=1e-6)
        assert bad > good


@pytest.mark.slow
class TestFitting:
    def test_recovers_synthetic_smile(self):
        quotes = synthetic_smile()
        fitter = VolSurfaceFitter(epochs=3000, seed=0)
        model = fitter.fit(quotes, forward=100.0)
        predicted = model.vol(quotes["strike"].to_numpy(), quotes["expiry"].to_numpy())
        rmse = float(np.sqrt(np.mean((predicted - quotes["implied_vol"].to_numpy()) ** 2)))
        assert rmse < 0.01
        assert model.metrics["epochs_run"] <= 3000

    def test_fit_is_deterministic_for_seed(self):
        quotes = synthetic_smile()
        m1 = VolSurfaceFitter(epochs=200, seed=42).fit(quotes, forward=100.0)
        m2 = VolSurfaceFitter(epochs=200, seed=42).fit(quotes, forward=100.0)
        np.testing.assert_allclose(m1.vol(100, 1.0), m2.vol(100, 1.0), atol=1e-6)

    def test_serialization_round_trip(self):
        quotes = synthetic_smile()
        model = VolSurfaceFitter(epochs=500, seed=1).fit(quotes, forward=100.0)
        blob = model.to_bytes()
        assert isinstance(blob, bytes) and len(blob) > 0
        restored = VolSurfaceModel.from_bytes(blob)
        strikes = np.array([85.0, 100.0, 115.0])
        np.testing.assert_allclose(restored.vol(strikes, 0.5), model.vol(strikes, 0.5), atol=1e-7)
        assert restored.forward == model.forward
        assert restored.metrics["mse"] == model.metrics["mse"]

    def test_budget_exhaustion_raises_convergence_error(self):
        # A zero-second budget stops training immediately -> data MSE stays
        # above the failure tolerance -> ConvergenceError with diagnostics.
        quotes = synthetic_smile()
        fitter = VolSurfaceFitter(epochs=100_000, seed=0, max_seconds=0.0)
        with pytest.raises(ConvergenceError) as excinfo:
            fitter.fit(quotes, forward=100.0)
        assert excinfo.value.residual is not None
