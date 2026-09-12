"""Feature engineering shared by training and serving.

``build_features`` (vectorized, for training frames) and
``build_feature_vector`` (single transaction, for the scoring hot path)
MUST stay behaviorally identical - training/serving skew is the fastest
way to silently ruin a fraud model.
"""

from __future__ import annotations

import hashlib
import math
from datetime import datetime

import numpy as np
import pandas as pd

FEATURE_COLUMNS: list[str] = ["log_amount", "hour_of_day", "merchant_category_hash"]


def category_hash(category: str) -> float:
    """Map a merchant category to a stable float in ``[0, 1)``."""
    return int(hashlib.sha256(category.encode("utf-8")).hexdigest(), 16) % 1000 / 1000.0


def build_features(df: pd.DataFrame) -> pd.DataFrame:
    """Derive model features from raw transaction columns (vectorized).

    Expects columns ``amount``, ``timestamp`` and ``merchant_category``.

    Args:
        df: Raw transactions frame.

    Returns:
        Copy of ``df`` with the ``FEATURE_COLUMNS`` added.
    """
    out = df.copy()
    out["log_amount"] = np.log1p(out["amount"].clip(lower=0))
    out["hour_of_day"] = pd.to_datetime(out["timestamp"]).dt.hour.astype(float)
    out["merchant_category_hash"] = out["merchant_category"].astype(str).map(category_hash)
    return out


def build_feature_vector(
    amount: float,
    merchant_category: str,
    timestamp: datetime,
    extra: dict[str, float] | None = None,
) -> dict[str, float]:
    """Derive the canonical feature vector for a single transaction.

    Args:
        amount: Transaction amount.
        merchant_category: Merchant category name/code.
        timestamp: Transaction timestamp.
        extra: Optional caller-provided extra features; canonical features
            always win on key collisions.

    Returns:
        Dict containing at least every name in :data:`FEATURE_COLUMNS`.
    """
    vector: dict[str, float] = {}
    if extra:
        vector.update({key: float(value) for key, value in extra.items()})
    vector["log_amount"] = math.log1p(max(float(amount), 0.0))
    vector["hour_of_day"] = float(timestamp.hour)
    vector["merchant_category_hash"] = category_hash(str(merchant_category))
    return vector
