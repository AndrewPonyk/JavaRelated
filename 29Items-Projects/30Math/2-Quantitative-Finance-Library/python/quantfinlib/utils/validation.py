"""Exception hierarchy and shared input validation.

Second line of defence in front of the C++ boundary: pydantic guards the
HTTP edge, this module guards the library edge (notebook users bypass HTTP).
"""

from __future__ import annotations

import numpy as np


class QuantFinError(Exception):
    """Base for all quantfinlib errors."""


class InvalidInputError(QuantFinError, ValueError):
    """The caller's inputs are wrong (fix the input, not the library)."""


class ConvergenceError(QuantFinError, RuntimeError):
    """A numerical routine failed to converge; carries diagnostics."""

    def __init__(
        self, message: str, *, iterations: int | None = None, residual: float | None = None
    ) -> None:
        super().__init__(message)
        self.iterations = iterations
        self.residual = residual


class ExtensionNotAvailable(QuantFinError, ImportError):
    """The compiled core is required (QF_REQUIRE_NATIVE) but missing."""


def broadcast_inputs(*arrays, names: tuple[str, ...]) -> tuple[np.ndarray, ...]:
    """Broadcast scalars/arrays to a common 1-D float64 shape and reject non-finite values.

    Returns C-contiguous arrays ready to cross the C++ boundary.
    """
    converted = []
    for name, a in zip(names, arrays, strict=True):
        arr = np.asarray(a, dtype=np.float64)
        if not np.all(np.isfinite(arr)):
            raise InvalidInputError(f"{name} contains NaN or infinite values")
        converted.append(arr)
    try:
        broadcast = np.broadcast_arrays(*converted)
    except ValueError as exc:
        shapes = {n: np.shape(a) for n, a in zip(names, arrays, strict=True)}
        raise InvalidInputError(f"inputs are not broadcastable: {shapes}") from exc
    return tuple(np.ascontiguousarray(b.ravel(), dtype=np.float64) for b in broadcast)
