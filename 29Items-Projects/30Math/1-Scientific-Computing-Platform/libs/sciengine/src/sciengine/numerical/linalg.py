"""Linear algebra utilities over NumPy/SciPy."""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from sciengine.exceptions import ComputationError

MAX_CHARPOLY_SIZE = 6  # symbolic characteristic polynomial only for small matrices


def solve_linear_system(a: np.ndarray, b: np.ndarray) -> np.ndarray:
    """Solve ``A x = b`` for square, well-conditioned A."""
    a = np.asarray(a, dtype=float)
    b = np.asarray(b, dtype=float)
    if a.ndim != 2 or a.shape[0] != a.shape[1]:
        raise ValueError(f"A must be square; got shape {a.shape}")
    try:
        return np.linalg.solve(a, b)
    except np.linalg.LinAlgError as exc:
        raise ComputationError(f"Linear system is singular or ill-conditioned: {exc}") from exc


@dataclass(frozen=True)
class EigenResult:
    values: np.ndarray
    vectors: np.ndarray  # columns are eigenvectors
    is_symmetric: bool
    condition_number: float
    char_poly_latex: str | None  # shown in the teaching UI for small matrices


def eigen_decomposition(a: np.ndarray) -> EigenResult:
    """Eigenvalues/eigenvectors with symmetry detection.

    Symmetric matrices route to ``eigh`` (real spectrum, orthonormal vectors);
    general matrices to ``eig``. For matrices up to 6×6 the symbolic
    characteristic polynomial is included for the teaching UI.
    """
    a = np.asarray(a, dtype=float)
    if a.ndim != 2 or a.shape[0] != a.shape[1]:
        raise ValueError(f"Matrix must be square; got shape {a.shape}")
    if not np.all(np.isfinite(a)):
        raise ValueError("Matrix entries must be finite.")

    is_symmetric = bool(np.allclose(a, a.T, rtol=1e-10, atol=1e-12))
    try:
        if is_symmetric:
            values, vectors = np.linalg.eigh(a)
        else:
            values, vectors = np.linalg.eig(a)
        condition_number = float(np.linalg.cond(a))
    except np.linalg.LinAlgError as exc:
        raise ComputationError(f"Eigendecomposition failed to converge: {exc}") from exc

    char_poly_latex = None
    if a.shape[0] <= MAX_CHARPOLY_SIZE:
        import sympy as sp

        lam = sp.Symbol("lambda")
        # nsimplify recovers exact entries for classroom matrices (integers,
        # simple fractions) so the polynomial prints cleanly.
        matrix = sp.nsimplify(sp.Matrix(a), rational=True)
        char_poly_latex = sp.latex(matrix.charpoly(lam).as_expr())

    return EigenResult(
        values=values,
        vectors=vectors,
        is_symmetric=is_symmetric,
        condition_number=condition_number,
        char_poly_latex=char_poly_latex,
    )
