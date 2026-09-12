# Type stubs for the compiled extension (cpp/bindings/bindings.cpp).
# The pure-Python fallback (quantfinlib._pure) implements the same surface.

import numpy as np
import numpy.typing as npt

__version__: str

class NativeConvergenceError(RuntimeError): ...

# --- Black-Scholes -----------------------------------------------------------

def bs_price(
    spot: float, strike: float, vol: float, rate: float, expiry: float, kind: str
) -> float: ...
def bs_price_batch(
    spot: npt.NDArray[np.float64],
    strike: npt.NDArray[np.float64],
    vol: npt.NDArray[np.float64],
    rate: npt.NDArray[np.float64],
    expiry: npt.NDArray[np.float64],
    kind: str,
) -> npt.NDArray[np.float64]: ...
def bs_greeks(
    spot: float, strike: float, vol: float, rate: float, expiry: float, kind: str
) -> dict[str, float]: ...
def bs_greeks_batch(
    spot: npt.NDArray[np.float64],
    strike: npt.NDArray[np.float64],
    vol: npt.NDArray[np.float64],
    rate: npt.NDArray[np.float64],
    expiry: npt.NDArray[np.float64],
    kind: str,
) -> dict[str, npt.NDArray[np.float64]]: ...
def bs_implied_vol(
    price: float, spot: float, strike: float, rate: float, expiry: float, kind: str
) -> float: ...

# --- American ------------------------------------------------------------------

def binomial_american(
    spot: float,
    strike: float,
    vol: float,
    rate: float,
    expiry: float,
    kind: str,
    n_steps: int = 1024,
) -> float: ...

# --- Monte Carlo -----------------------------------------------------------------

def mc_price_european(
    spot: float,
    strike: float,
    vol: float,
    rate: float,
    expiry: float,
    kind: str,
    n_paths: int = 100000,
    seed: int = 42,
    antithetic: bool = True,
) -> dict[str, float | int]: ...
def mc_price_asian(
    spot: float,
    strike: float,
    vol: float,
    rate: float,
    expiry: float,
    kind: str,
    n_steps: int = 252,
    n_paths: int = 100000,
    seed: int = 42,
    antithetic: bool = True,
) -> dict[str, float | int]: ...
def mc_price_barrier(
    spot: float,
    strike: float,
    vol: float,
    rate: float,
    expiry: float,
    kind: str,
    barrier: float,
    barrier_type: str,
    n_steps: int = 252,
    n_paths: int = 100000,
    seed: int = 42,
    antithetic: bool = True,
) -> dict[str, float | int]: ...

# --- Risk --------------------------------------------------------------------------

def historical_var(
    returns: npt.NDArray[np.float64], confidence: float = 0.99
) -> dict[str, float]: ...
def parametric_var(
    returns: npt.NDArray[np.float64], confidence: float = 0.99
) -> dict[str, float]: ...
