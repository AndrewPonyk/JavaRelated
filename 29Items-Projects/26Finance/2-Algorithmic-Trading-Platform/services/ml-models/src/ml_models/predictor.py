"""Lightweight price-direction predictor (pure NumPy).

A real logistic-regression model over momentum features. It runs anywhere (no
torch needed) and is the default served by the inference service. The heavy
Transformer in ``ml_models.transformers`` is the offline-trained alternative.
"""

from __future__ import annotations

import numpy as np

from trading_common.indicators import rsi

# Feature vector: [bias, 1-bar return, 5-bar return, RSI-centered, EMA-slope].
N_FEATURES = 5


def featurize(closes: np.ndarray) -> np.ndarray:
    """Build a feature vector from a window of closing prices."""
    closes = np.asarray(closes, dtype=np.float64)
    if closes.size < 7:
        return np.zeros(N_FEATURES)
    r1 = closes[-1] / closes[-2] - 1.0
    r5 = closes[-1] / closes[-6] - 1.0
    rsi_vals = rsi(closes, 14)
    rsi_c = (rsi_vals[-1] / 100.0 - 0.5) if not np.isnan(rsi_vals[-1]) else 0.0
    slope = (closes[-1] - closes[-4]) / closes[-4]
    return np.array([1.0, r1, r5, rsi_c, slope], dtype=np.float64)


def _sigmoid(z: float) -> float:
    return float(1.0 / (1.0 + np.exp(-z)))


class MomentumLogisticPredictor:
    """Logistic regression on momentum features. Trainable via gradient descent;
    ships with sensible default weights (positive momentum -> higher P(up))."""

    def __init__(self, weights: np.ndarray | None = None) -> None:
        # default: bias 0, weak positive loadings on momentum features
        self.weights = (
            np.array([0.0, 8.0, 4.0, 1.0, 6.0]) if weights is None else np.asarray(weights, float)
        )

    def predict_proba_up(self, closes: np.ndarray) -> float:
        x = featurize(closes)
        if not x.any():
            return 0.5  # not enough data => no edge
        return _sigmoid(float(self.weights @ x))

    def fit(
        self, windows: list[np.ndarray], labels: list[int], *, epochs: int = 200, lr: float = 0.1
    ) -> MomentumLogisticPredictor:
        """Train on (price-window, up/down label) pairs via logistic GD."""
        x = np.array([featurize(w) for w in windows], dtype=np.float64)
        y = np.asarray(labels, dtype=np.float64)
        w = self.weights.copy()
        n = max(len(y), 1)
        for _ in range(epochs):
            preds = 1.0 / (1.0 + np.exp(-(x @ w)))
            grad = x.T @ (preds - y) / n
            w -= lr * grad
        self.weights = w
        return self

    @staticmethod
    def make_training_set(
        series: np.ndarray, window: int = 30
    ) -> tuple[list[np.ndarray], list[int]]:
        """Build supervised examples from a price series: predict next-bar direction."""
        series = np.asarray(series, dtype=np.float64)
        windows, labels = [], []
        for i in range(window, series.size - 1):
            windows.append(series[i - window : i + 1])
            labels.append(1 if series[i + 1] > series[i] else 0)
        return windows, labels
