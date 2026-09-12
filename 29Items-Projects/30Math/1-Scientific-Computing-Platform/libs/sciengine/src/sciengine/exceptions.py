"""Error taxonomy shared by the API, workers, and notebooks.

Every error carries a stable machine-readable ``code``. The backend maps codes
to HTTP statuses and RFC 7807 problem types (see docs/ARCHITECTURE.md §2.6),
so changing a code here is an API contract change.
"""

from __future__ import annotations


class SciEngineError(Exception):
    """Base class for all sciengine errors."""

    code: str = "sciengine_error"


class ExpressionParseError(SciEngineError):
    """User input could not be parsed into a mathematical expression.

    Messages must be user-presentable: they tell the student what to fix.
    """

    code = "expression_parse_error"


class UnsupportedExpressionError(SciEngineError):
    """Valid math that this engine cannot (yet) handle."""

    code = "unsupported_expression"


class ComputationError(SciEngineError):
    """The computation itself failed — our problem, not the user's."""

    code = "computation_error"


class ConvergenceError(ComputationError):
    """A numerical method failed to converge within its iteration budget."""

    code = "convergence_error"

    def __init__(
        self,
        message: str,
        *,
        iterations: int | None = None,
        residual: float | None = None,
    ) -> None:
        super().__init__(message)
        self.iterations = iterations
        self.residual = residual


class ComputationTimeoutError(SciEngineError):
    """Computation exceeded its time budget (enforced by the caller's sandbox)."""

    code = "computation_timeout"
