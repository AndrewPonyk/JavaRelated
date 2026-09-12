"""Native-extension loader with pure-Python fallback.

Every numerical façade imports `backend` from here. If the compiled
`_qfcore` module is present it is used; otherwise a NumPy/SciPy reference
implementation takes over (identical results to ~1e-12, slower). The
fallback doubles as the cross-check oracle in tests.
"""

from __future__ import annotations

import logging
import warnings

log = logging.getLogger("quantfinlib")

try:
    from quantfinlib import _qfcore as backend

    HAS_NATIVE = True
except ImportError:  # pragma: no cover - exercised only in sdist installs
    from quantfinlib import _pure as backend  # type: ignore[no-redef]

    HAS_NATIVE = False
    warnings.warn(
        "quantfinlib: compiled core (_qfcore) not available; using the pure-Python "
        "fallback. Results are identical but 10-100x slower. Install a binary wheel "
        "or build from source with a C++17 compiler.",
        RuntimeWarning,
        stacklevel=2,
    )

__all__ = ["HAS_NATIVE", "backend"]
