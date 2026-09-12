"""Neural network definitions for volatility-surface fitting.

Kept separate from the fitting logic so architectures can be swapped
(MLP → SVI-parameterised net → Gaussian process) without touching the
training loop or persistence code.
"""

from __future__ import annotations

from collections.abc import Callable


def _require_torch():
    try:
        import torch

        return torch
    except ImportError as exc:  # pragma: no cover
        raise ImportError("quantfinlib.ml requires torch: pip install 'quantfinlib[ml]'") from exc


def build_mlp_surface(hidden_sizes: tuple[int, ...] = (64, 64, 64)):
    """MLP mapping (log-moneyness, sqrt-expiry) → implied vol.

    Softplus output keeps σ > 0 by construction. Inputs are expected
    standardized by the fitter.
    """
    torch = _require_torch()
    nn = torch.nn

    layers: list = []
    in_features = 2  # (log(K/F), sqrt(T))
    for h in hidden_sizes:
        layers += [nn.Linear(in_features, h), nn.SiLU()]
        in_features = h
    layers += [nn.Linear(in_features, 1), nn.Softplus()]
    return nn.Sequential(*layers)


def arbitrage_penalty(surface_fn: Callable, k_grid, t_grid):
    """Soft no-arbitrage penalties evaluated on a (log-moneyness, expiry) grid.

    `surface_fn(k, t)` must map real-coordinate tensors (k = log-moneyness,
    t = expiry in years) to implied vol σ, differentiably. Two conditions on
    total variance w(k, T) = σ²T are penalised:

      * calendar spread:  ∂w/∂T >= 0  (total variance non-decreasing in expiry)
      * butterfly (Durrleman):
            g(k) = (1 - k·w'/(2w))² - (w'²/4)(1/w + 1/4) + w''/2 >= 0
        where ' denotes ∂/∂k. g >= 0 keeps the implied density non-negative.

    Returns a scalar tensor (mean of the violations) to add to the data loss.
    """
    torch = _require_torch()

    k = k_grid.detach().clone().requires_grad_(True)
    t = t_grid.detach().clone().requires_grad_(True)
    sigma = surface_fn(k, t)
    w = (sigma**2 * t).clamp_min(1e-8)  # total variance

    ones = torch.ones_like(w)
    dw_dk, dw_dt = torch.autograd.grad(w, (k, t), grad_outputs=ones, create_graph=True)
    (d2w_dk2,) = torch.autograd.grad(
        dw_dk, k, grad_outputs=torch.ones_like(dw_dk), create_graph=True
    )

    calendar = torch.relu(-dw_dt)
    g = (1.0 - k * dw_dk / (2.0 * w)) ** 2 - (dw_dk**2 / 4.0) * (1.0 / w + 0.25) + d2w_dk2 / 2.0
    butterfly = torch.relu(-g)
    return calendar.mean() + butterfly.mean()
