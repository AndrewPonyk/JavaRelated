"""Model evaluation metrics and CI quality gates."""

from __future__ import annotations

import numpy as np
from sklearn.metrics import f1_score, precision_score, recall_score, roc_auc_score

DECISION_THRESHOLD = 0.5


def evaluate_model(y_true: np.ndarray, y_prob: np.ndarray) -> dict[str, float]:
    """Compute the standard classification metrics at the decision threshold.

    Args:
        y_true: Ground-truth binary labels.
        y_prob: Predicted fraud probabilities.

    Returns:
        Dict with ``auc``, ``precision``, ``recall``, ``f1``, ``threshold``
        and ``positive_rate``.

    Raises:
        ValueError: If the samples are empty, mismatched, or single-class
            (AUC is undefined without both classes in ``y_true``).
    """
    y_true = np.asarray(y_true)
    y_prob = np.asarray(y_prob)
    if y_true.size == 0 or y_true.shape != y_prob.shape:
        raise ValueError("y_true and y_prob must be non-empty arrays of identical shape")
    if np.unique(y_true).size < 2:
        raise ValueError(
            "evaluation set contains a single class; AUC is undefined - "
            "check the train/test split stratification and the data source"
        )
    y_pred = (y_prob >= DECISION_THRESHOLD).astype(int)
    return {
        "auc": float(roc_auc_score(y_true, y_prob)),
        "precision": float(precision_score(y_true, y_pred, zero_division=0)),
        "recall": float(recall_score(y_true, y_pred, zero_division=0)),
        "f1": float(f1_score(y_true, y_pred, zero_division=0)),
        "threshold": DECISION_THRESHOLD,
        "positive_rate": float(y_pred.mean()),
    }


def passes_quality_gates(
    metrics: dict[str, float], min_auc: float = 0.85, min_recall: float = 0.7
) -> tuple[bool, list[str]]:
    """Check evaluation metrics against the promotion quality gates.

    Args:
        metrics: Output of :func:`evaluate_model`.
        min_auc: Minimum acceptable ROC AUC.
        min_recall: Minimum acceptable recall (missed fraud is expensive).

    Returns:
        Tuple ``(passed, reasons)`` where ``reasons`` lists every failed gate.
    """
    reasons: list[str] = []
    if metrics.get("auc", 0.0) < min_auc:
        reasons.append(f"auc {metrics.get('auc', 0.0):.4f} < required {min_auc:.4f}")
    if metrics.get("recall", 0.0) < min_recall:
        reasons.append(f"recall {metrics.get('recall', 0.0):.4f} < required {min_recall:.4f}")
    return (len(reasons) == 0, reasons)
