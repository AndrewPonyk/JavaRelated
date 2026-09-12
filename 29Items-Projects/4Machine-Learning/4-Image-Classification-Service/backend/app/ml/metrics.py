"""Evaluation metrics and per-label threshold calibration for multi-label models."""

from __future__ import annotations

import numpy as np


def sigmoid(x: np.ndarray) -> np.ndarray:
    return 1.0 / (1.0 + np.exp(-x))


def macro_f1(y_true: np.ndarray, y_pred: np.ndarray) -> float:
    """Macro-averaged F1 over labels. ``y_true``/``y_pred`` are {0,1} arrays (N, L)."""
    tp = (y_pred * y_true).sum(axis=0)
    fp = (y_pred * (1 - y_true)).sum(axis=0)
    fn = ((1 - y_pred) * y_true).sum(axis=0)
    precision = np.divide(tp, tp + fp, out=np.zeros_like(tp, dtype=float), where=(tp + fp) > 0)
    recall = np.divide(tp, tp + fn, out=np.zeros_like(tp, dtype=float), where=(tp + fn) > 0)
    f1 = np.divide(
        2 * precision * recall,
        precision + recall,
        out=np.zeros_like(precision),
        where=(precision + recall) > 0,
    )
    return float(f1.mean())


def calibrate_thresholds(
    y_true: np.ndarray,
    scores: np.ndarray,
    grid: np.ndarray | None = None,
    default: float = 0.5,
) -> list[float]:
    """Pick, per label, the decision threshold that maximizes that label's F1.

    ``scores`` are probabilities (N, L) in [0, 1]. Returns one threshold per label.
    Labels with no positive examples fall back to ``default``.
    """
    if grid is None:
        grid = np.linspace(0.05, 0.95, 19)
    num_labels = scores.shape[1]
    thresholds: list[float] = []
    for label in range(num_labels):
        col_true = y_true[:, label]
        col_score = scores[:, label]
        if col_true.sum() == 0:
            thresholds.append(default)
            continue
        best_t, best_f1 = default, -1.0
        for t in grid:
            pred = (col_score >= t).astype(int)
            tp = int((pred * col_true).sum())
            fp = int((pred * (1 - col_true)).sum())
            fn = int(((1 - pred) * col_true).sum())
            precision = tp / (tp + fp) if (tp + fp) else 0.0
            recall = tp / (tp + fn) if (tp + fn) else 0.0
            f1 = 2 * precision * recall / (precision + recall) if (precision + recall) else 0.0
            if f1 > best_f1:
                best_f1, best_t = f1, float(t)
        thresholds.append(best_t)
    return thresholds
