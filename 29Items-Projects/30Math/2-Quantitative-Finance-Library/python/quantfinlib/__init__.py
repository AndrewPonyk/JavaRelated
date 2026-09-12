"""quantfinlib — quantitative finance library with a C++ core.

Public surface:
    quantfinlib.options   Black-Scholes + Monte Carlo pricing
    quantfinlib.risk      VaR / Expected Shortfall
    quantfinlib.ml        neural-net volatility-surface fitting (requires [ml] extra)

The compiled extension `quantfinlib._qfcore` is an implementation detail;
`quantfinlib._compat.HAS_NATIVE` reports whether it loaded.
"""

import logging

from quantfinlib import options, risk
from quantfinlib._compat import HAS_NATIVE
from quantfinlib._version import __version__
from quantfinlib.utils.validation import (
    ConvergenceError,
    ExtensionNotAvailable,
    InvalidInputError,
    QuantFinError,
)

__all__ = [
    "HAS_NATIVE",
    "ConvergenceError",
    "ExtensionNotAvailable",
    "InvalidInputError",
    "QuantFinError",
    "__version__",
    "options",
    "risk",
]

# Library etiquette: stay silent unless the app configures logging.
logging.getLogger("quantfinlib").addHandler(logging.NullHandler())
