"""Linear algebra tests against hand-computable spectra."""

import numpy as np
import pytest

from sciengine.exceptions import ComputationError
from sciengine.numerical.linalg import eigen_decomposition, solve_linear_system


def test_solve_linear_system():
    a = [[2.0, 1.0], [1.0, 3.0]]
    b = [3.0, 5.0]
    x = solve_linear_system(np.asarray(a), np.asarray(b))
    assert np.allclose(np.asarray(a) @ x, b)


def test_singular_system_raises_computation_error():
    with pytest.raises(ComputationError, match="singular"):
        solve_linear_system(np.asarray([[1.0, 2.0], [2.0, 4.0]]), np.asarray([1.0, 2.0]))


def test_symmetric_eigendecomposition_known_spectrum():
    # [[2,1],[1,2]] has eigenvalues 1 and 3.
    result = eigen_decomposition(np.asarray([[2.0, 1.0], [1.0, 2.0]]))
    assert result.is_symmetric
    assert np.allclose(np.sort(result.values), [1.0, 3.0])
    # A v = λ v for each eigenpair
    a = np.asarray([[2.0, 1.0], [1.0, 2.0]])
    for i in range(2):
        assert np.allclose(a @ result.vectors[:, i], result.values[i] * result.vectors[:, i])
    assert result.char_poly_latex is not None
    assert "lambda" in result.char_poly_latex


def test_nonsymmetric_matrix_routes_to_general_solver():
    result = eigen_decomposition(np.asarray([[0.0, 1.0], [-1.0, 0.0]]))  # rotation: ±i
    assert not result.is_symmetric
    assert np.allclose(np.sort_complex(result.values), [-1j, 1j])


def test_large_matrix_skips_char_poly():
    result = eigen_decomposition(np.eye(8))
    assert result.char_poly_latex is None
    assert np.allclose(result.values, np.ones(8))
