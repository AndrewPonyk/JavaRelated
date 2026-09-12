"""Neural-network implied-volatility surface fitting.

Pipeline: option quotes (DataFrame) → standardized tensors → MLP fit with
no-arbitrage penalties (calendar + butterfly) → queryable, serializable
surface. Fits are deterministic for a given seed.
"""

from __future__ import annotations

import io
import time
from dataclasses import dataclass, field
from typing import Any

import numpy as np
import pandas as pd

from quantfinlib.utils.validation import ConvergenceError, InvalidInputError

REQUIRED_COLUMNS = ("strike", "expiry", "implied_vol")
_SERIALIZATION_VERSION = 1

# A fit that ends above this data-MSE is reported as failed rather than
# returning a silently bad surface.
FAIL_MSE = 1e-2
# Below this data-MSE the surface is fitted for all practical purposes;
# training stops early.
CONVERGED_MSE = 1e-6


@dataclass
class VolSurfaceModel:
    """A fitted surface: query σ(K, T); serializable for the API's DB."""

    net: Any  # torch.nn.Module (eval mode, on CPU)
    norm: dict[str, float]  # input standardization constants
    forward: float  # forward price the moneyness axis is relative to
    hidden_sizes: tuple[int, ...]
    metrics: dict[str, float] = field(default_factory=dict)

    def vol(self, strike, expiry) -> np.ndarray:
        """Implied vol at arbitrary (strike, expiry) points (vectorised)."""
        import torch

        strike_arr = np.asarray(strike, dtype=np.float64)
        expiry_arr = np.asarray(expiry, dtype=np.float64)
        if np.any(strike_arr <= 0) or not np.all(np.isfinite(strike_arr)):
            raise InvalidInputError("strike must be > 0 and finite")
        if np.any(expiry_arr <= 0) or not np.all(np.isfinite(expiry_arr)):
            raise InvalidInputError("expiry must be > 0 and finite")

        k = np.log(strike_arr / self.forward)
        t = np.sqrt(expiry_arr)
        kb, tb = np.broadcast_arrays(k, t)
        x = np.stack(
            [
                (kb.ravel() - self.norm["k_mean"]) / self.norm["k_std"],
                (tb.ravel() - self.norm["t_mean"]) / self.norm["t_std"],
            ],
            axis=1,
        )
        with torch.no_grad():
            out = self.net(torch.as_tensor(x, dtype=torch.float32)).numpy().ravel()
        return out.reshape(kb.shape)

    def to_bytes(self) -> bytes:
        """Serialize weights + normalization into a versioned envelope
        (stored in the API's vol_surfaces table)."""
        import torch

        buffer = io.BytesIO()
        torch.save(
            {
                "version": _SERIALIZATION_VERSION,
                "state_dict": self.net.state_dict(),
                "norm": self.norm,
                "forward": self.forward,
                "hidden_sizes": tuple(self.hidden_sizes),
                "metrics": self.metrics,
            },
            buffer,
        )
        return buffer.getvalue()

    @classmethod
    def from_bytes(cls, blob: bytes) -> VolSurfaceModel:
        import torch

        from quantfinlib.ml.models import build_mlp_surface

        envelope = torch.load(io.BytesIO(blob), weights_only=True)
        if envelope.get("version") != _SERIALIZATION_VERSION:
            raise InvalidInputError(
                f"unsupported surface serialization version {envelope.get('version')!r}"
            )
        hidden_sizes = tuple(envelope["hidden_sizes"])
        net = build_mlp_surface(hidden_sizes)
        net.load_state_dict(envelope["state_dict"])
        net.eval()
        return cls(
            net=net,
            norm=dict(envelope["norm"]),
            forward=float(envelope["forward"]),
            hidden_sizes=hidden_sizes,
            metrics=dict(envelope["metrics"]),
        )


class VolSurfaceFitter:
    """Fits a VolSurfaceModel from market quotes.

    Parameters mirror the API's fit-request schema so the service layer can
    pass them through 1:1. `max_seconds` bounds wall-clock training time
    (the API sets it from QF_ML_MAX_FIT_SECONDS).
    """

    def __init__(
        self,
        *,
        hidden_sizes: tuple[int, ...] = (64, 64, 64),
        epochs: int = 2000,
        learning_rate: float = 1e-3,
        arbitrage_weight: float = 0.1,
        seed: int = 42,
        device: str = "cpu",
        max_seconds: float | None = None,
    ) -> None:
        self.hidden_sizes = tuple(hidden_sizes)
        self.epochs = epochs
        self.learning_rate = learning_rate
        self.arbitrage_weight = arbitrage_weight
        self.seed = seed
        self.device = device
        self.max_seconds = max_seconds

    def fit(self, quotes: pd.DataFrame, forward: float) -> VolSurfaceModel:
        """Fit a surface to quotes with columns {strike, expiry, implied_vol}.

        Raises InvalidInputError on malformed quotes and ConvergenceError if
        training ends (or runs out of budget) above the failure tolerance.
        """
        self._validate_quotes(quotes)
        if not (forward > 0 and np.isfinite(forward)):
            raise InvalidInputError("forward must be > 0 and finite")

        import torch

        from quantfinlib.ml.models import arbitrage_penalty, build_mlp_surface

        torch.manual_seed(self.seed)

        k = np.log(quotes["strike"].to_numpy(np.float64) / forward)
        t = np.sqrt(quotes["expiry"].to_numpy(np.float64))
        norm = {
            "k_mean": float(k.mean()),
            "k_std": float(k.std() or 1.0),
            "t_mean": float(t.mean()),
            "t_std": float(t.std() or 1.0),
        }
        x = np.stack(
            [(k - norm["k_mean"]) / norm["k_std"], (t - norm["t_mean"]) / norm["t_std"]], axis=1
        )
        y = quotes["implied_vol"].to_numpy(np.float64).reshape(-1, 1)

        net = build_mlp_surface(self.hidden_sizes).to(self.device)
        xt = torch.as_tensor(x, dtype=torch.float32, device=self.device)
        yt = torch.as_tensor(y, dtype=torch.float32, device=self.device)
        optimizer = torch.optim.Adam(net.parameters(), lr=self.learning_rate)

        # Penalty grid spans the quoted region in real coordinates.
        expiries = quotes["expiry"].to_numpy(np.float64)
        k_lin = torch.linspace(float(k.min()), float(k.max()), 8, device=self.device)
        t_lin = torch.linspace(float(expiries.min()), float(expiries.max()), 4, device=self.device)
        k_grid, t_grid = (g.reshape(-1) for g in torch.meshgrid(k_lin, t_lin, indexing="ij"))

        def surface_fn(k_real, t_real):
            xin = torch.stack(
                [
                    (k_real - norm["k_mean"]) / norm["k_std"],
                    (torch.sqrt(t_real) - norm["t_mean"]) / norm["t_std"],
                ],
                dim=1,
            )
            return net(xin).squeeze(-1)

        deadline = None if self.max_seconds is None else time.monotonic() + self.max_seconds
        data_loss_value = float("inf")
        penalty_value = 0.0
        epochs_run = 0
        for epoch in range(1, self.epochs + 1):
            optimizer.zero_grad()
            data_loss = torch.nn.functional.mse_loss(net(xt), yt)
            if self.arbitrage_weight > 0.0:
                penalty = arbitrage_penalty(surface_fn, k_grid, t_grid)
            else:
                penalty = torch.zeros((), device=self.device)
            loss = data_loss + self.arbitrage_weight * penalty
            loss.backward()
            optimizer.step()

            data_loss_value = float(data_loss.detach())
            penalty_value = float(penalty.detach())
            epochs_run = epoch
            if data_loss_value < CONVERGED_MSE:
                break  # early stop: fitted for all practical purposes
            if deadline is not None and epoch % 25 == 0 and time.monotonic() > deadline:
                break  # out of budget; acceptability judged below

        if data_loss_value > FAIL_MSE:
            raise ConvergenceError(
                f"surface fit did not converge (data MSE {data_loss_value:.4g} after "
                f"{epochs_run} epochs)",
                iterations=epochs_run,
                residual=data_loss_value,
            )

        net = net.cpu().eval()
        return VolSurfaceModel(
            net=net,
            norm=norm,
            forward=float(forward),
            hidden_sizes=self.hidden_sizes,
            metrics={
                "mse": data_loss_value,
                "arbitrage_penalty": penalty_value,
                "n_quotes": float(len(quotes)),
                "epochs_run": float(epochs_run),
            },
        )

    @staticmethod
    def _validate_quotes(quotes: pd.DataFrame) -> None:
        missing = set(REQUIRED_COLUMNS) - set(quotes.columns)
        if missing:
            raise InvalidInputError(f"quotes missing columns: {sorted(missing)}")
        if len(quotes) < 10:
            raise InvalidInputError(f"need >= 10 quotes to fit a surface, got {len(quotes)}")
        for col in REQUIRED_COLUMNS:
            values = quotes[col].to_numpy(np.float64)
            if not np.all(np.isfinite(values)) or not np.all(values > 0):
                raise InvalidInputError(
                    f"column {col!r} must be finite and > 0 "
                    "(one bad quote NaN-poisons the whole surface)"
                )
