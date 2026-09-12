"""sciengine — the scientific kernel of the Scientific Computing Platform.

Pure computational library (NumPy / SciPy / SymPy / Matplotlib) with **no**
web-framework or AWS dependencies. It is consumed by:

- the FastAPI backend (synchronous fast path),
- Celery compute workers (heavy, sandboxed jobs),
- Jupyter notebooks (interactive teaching material),
- SageMaker training code (feature extraction parity).

Anything mathematical belongs here; anything HTTP/persistence-related does not.
"""

from sciengine.exceptions import (
    ComputationError,
    ComputationTimeoutError,
    ExpressionParseError,
    SciEngineError,
    UnsupportedExpressionError,
)

__version__ = "0.1.0"

__all__ = [
    "ComputationError",
    "ComputationTimeoutError",
    "ExpressionParseError",
    "SciEngineError",
    "UnsupportedExpressionError",
    "__version__",
]
