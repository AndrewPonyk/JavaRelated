"""quantfinlib quickstart — run: python examples/quickstart.py"""

import numpy as np
import pandas as pd

import quantfinlib
from quantfinlib.options import black_scholes as bs
from quantfinlib.options import monte_carlo as mc
from quantfinlib.options import price_american
from quantfinlib.risk import portfolio_var, value_at_risk

print(f"quantfinlib {quantfinlib.__version__} (native core: {quantfinlib.HAS_NATIVE})\n")

# --- Black-Scholes: price a whole strike chain in one call --------------------
strikes = np.linspace(80, 120, 9)
prices = bs.price(spot=100, strike=strikes, vol=0.2, rate=0.05, expiry=1.0, kind="call")
chain_greeks = bs.greeks(spot=100, strike=strikes, vol=0.2, rate=0.05, expiry=1.0, kind="call")
print("Call chain with Greeks (S=100, vol=20%, r=5%, T=1y):")
print(
    pd.DataFrame(
        {
            "strike": strikes,
            "price": prices,
            "delta": chain_greeks["delta"],
            "gamma": chain_greeks["gamma"],
        }
    ).to_string(index=False),
    "\n",
)

# --- Implied vol round trip -----------------------------------------------------
iv = bs.implied_vol(price=float(prices[4]), spot=100, strike=100, rate=0.05, expiry=1.0)
print(f"Implied vol round-trip: {iv:.6f} (expected 0.200000)")

# --- American vs European -------------------------------------------------------
amer_put = price_american(spot=100, strike=110, vol=0.2, rate=0.05, expiry=1.0, kind="put")
euro_put = bs.price(spot=100, strike=110, vol=0.2, rate=0.05, expiry=1.0, kind="put")
print(
    f"American put {amer_put:.4f} vs European {euro_put:.4f} "
    f"(early-exercise premium {amer_put - euro_put:.4f})\n"
)

# --- Monte Carlo: European with pathwise Greeks, Asian, barrier -------------------
result = mc.price_european(100, 100, 0.2, 0.05, 1.0, "call", n_paths=500_000, seed=42)
lo, hi = result.confidence_interval()
print(f"MC European: {result.price:.4f} ± {result.std_error:.4f} (95% CI [{lo:.4f}, {hi:.4f}])")
print(
    f"  pathwise delta {result.delta:.4f} / vega {result.vega:.2f} "
    f"(closed form {bs.greeks(100, 100, 0.2, 0.05, 1.0, 'call')['delta']:.4f} / "
    f"{bs.greeks(100, 100, 0.2, 0.05, 1.0, 'call')['vega']:.2f})"
)
asian = mc.price_asian(100, 100, 0.2, 0.05, 1.0, "call", n_steps=252, n_paths=100_000, seed=42)
knockout = mc.price_barrier(
    100,
    100,
    0.2,
    0.05,
    1.0,
    "call",
    barrier=130.0,
    barrier_type="up-out",
    n_steps=252,
    n_paths=100_000,
    seed=42,
)
print(f"MC Asian (arith. avg): {asian.price:.4f}   up-and-out @130: {knockout.price:.4f}\n")

# --- Risk: portfolio VaR ---------------------------------------------------------
rng = np.random.default_rng(0)
returns = pd.DataFrame(
    {
        "equities": rng.normal(0.0004, 0.012, 1000),
        "bonds": rng.normal(0.0001, 0.004, 1000),
    }
)
report = value_at_risk(returns["equities"], confidence=0.99)
print(f"Equities 99% 1-day VaR: {report.var:.2%}  ES: {report.expected_shortfall:.2%}")
port = portfolio_var(returns, weights=[0.6, 0.4], confidence=0.99)
print(f"60/40 portfolio VaR:    {port.var:.2%}  ES: {port.expected_shortfall:.2%}\n")

# --- ML vol surface (requires quantfinlib[ml]) ------------------------------------
try:
    from quantfinlib.ml import VolSurfaceFitter

    k = np.log(np.tile(np.linspace(80, 120, 15), 2) / 100.0)
    expiries = np.repeat([0.25, 1.0], 15)
    quotes = pd.DataFrame(
        {
            "strike": np.tile(np.linspace(80, 120, 15), 2),
            "expiry": expiries,
            "implied_vol": 0.2 + 0.3 * k**2 + 0.02 * expiries,
        }
    )
    surface = VolSurfaceFitter(epochs=800, seed=0).fit(quotes, forward=100.0)
    print(
        f"Fitted vol surface: MSE {surface.metrics['mse']:.2e} "
        f"in {surface.metrics['epochs_run']:.0f} epochs"
    )
    # "sigma" not the Greek letter: Windows consoles often run cp1252.
    print(f"  sigma(K=105, T=0.5) = {float(surface.vol(105, 0.5)):.4f}")
except ImportError:
    print("Vol-surface demo skipped: install quantfinlib[ml] for torch support.")
