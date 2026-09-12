from __future__ import annotations

import numpy as np

from app.analytics import annualized_covariance, estimate_mu_sigma, to_returns
from app.analytics.returns import shrunk_covariance


def test_to_returns_shapes(prices):
    log_r = to_returns(prices, method="log")
    simple_r = to_returns(prices, method="simple")
    # One row is consumed by differencing.
    assert len(log_r) == len(prices) - 1
    assert log_r.shape[1] == prices.shape[1]
    assert simple_r.shape == log_r.shape


def test_estimate_mu_sigma_well_formed(prices):
    mu, cov, labels = estimate_mu_sigma(prices)
    n = prices.shape[1]
    assert mu.shape == (n,)
    assert cov.shape == (n, n)
    assert labels == list(prices.columns)
    # Covariance must be symmetric and positive semi-definite.
    np.testing.assert_allclose(cov, cov.T, atol=1e-12)
    assert np.all(np.linalg.eigvalsh(cov) > -1e-10)


def test_shrinkage_moves_toward_diagonal(prices):
    cov = annualized_covariance(to_returns(prices))
    shrunk = shrunk_covariance(cov, delta=0.5)
    off_diag_before = (cov.to_numpy() - np.diag(np.diag(cov.to_numpy()))).sum()
    off_diag_after = (shrunk.to_numpy() - np.diag(np.diag(shrunk.to_numpy()))).sum()
    assert abs(off_diag_after) < abs(off_diag_before)
