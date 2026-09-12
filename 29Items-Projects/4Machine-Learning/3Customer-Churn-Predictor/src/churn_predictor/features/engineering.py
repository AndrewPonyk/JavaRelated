"""Domain feature engineering.

This module is the **single source of truth** for derived features, imported by
both the training pipeline and the serving path to guarantee no train/serve skew.
"""

from __future__ import annotations

import logging

import numpy as np
import pandas as pd

logger = logging.getLogger(__name__)


def add_tenure_buckets(df: pd.DataFrame) -> pd.DataFrame:
    """Bucket raw tenure into coarse lifecycle stages."""
    out = df.copy()
    if "tenure_months" in out.columns:
        out["tenure_bucket"] = pd.cut(
            out["tenure_months"],
            bins=[-1, 6, 12, 24, 48, np.inf],
            labels=["0-6m", "6-12m", "1-2y", "2-4y", "4y+"],
        ).astype(str)
    return out


def add_usage_ratios(df: pd.DataFrame) -> pd.DataFrame:
    """Derive intensity/affordability ratios that often signal churn risk."""
    out = df.copy()
    if {"total_charges", "tenure_months"}.issubset(out.columns):
        # Average spend per month of tenure (guard against divide-by-zero).
        out["avg_monthly_spend"] = out["total_charges"] / out["tenure_months"].clip(lower=1)
    if {"num_support_tickets", "tenure_months"}.issubset(out.columns):
        out["tickets_per_month"] = out["num_support_tickets"] / out["tenure_months"].clip(lower=1)
    return out


def build_features(df: pd.DataFrame) -> pd.DataFrame:
    """Apply all engineered features in a deterministic order.

    Extension point: interaction terms, recency/frequency, and seasonality
    features can be added here; both training and serving call this function.
    """
    out = add_tenure_buckets(df)
    out = add_usage_ratios(out)
    logger.debug("Engineered features; columns now: %s", list(out.columns))
    return out
