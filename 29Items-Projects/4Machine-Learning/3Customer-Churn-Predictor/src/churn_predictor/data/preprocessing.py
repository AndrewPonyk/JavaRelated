"""Cleaning, encoding, feature assembly, and train/test splitting.

CRITICAL: the :class:`Preprocessor` is fit on the **training split only** to avoid
data leakage, then persisted alongside the model so the exact same transforms run
at serving time (no train/serve skew — see TECH-NOTES pitfalls #1 and #9).
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field

import numpy as np
import pandas as pd
from sklearn.model_selection import train_test_split

from churn_predictor import PreprocessingError
from churn_predictor.config import settings
from churn_predictor.features.engineering import build_features

logger = logging.getLogger(__name__)

TARGET = "is_churned"
ID_COLUMN = "customer_id"
CATEGORICAL = ("contract_type", "payment_method")
NUMERIC = ("tenure_months", "monthly_charges", "total_charges", "num_support_tickets")


@dataclass
class SplitData:
    """Container for a train/test split (raw features, target separated)."""

    X_train: pd.DataFrame
    X_test: pd.DataFrame
    y_train: pd.Series
    y_test: pd.Series


# ---------------------------------------------------------------------------
# Stateless helpers (kept for utility / backwards-compatible tests)
# ---------------------------------------------------------------------------
def coerce_numeric(df: pd.DataFrame) -> pd.DataFrame:
    """Coerce known numeric columns to numeric dtype (no imputation)."""
    out = df.copy()
    for col in NUMERIC:
        if col in out.columns:
            out[col] = pd.to_numeric(out[col], errors="coerce")
    return out


def clean(df: pd.DataFrame) -> pd.DataFrame:
    """Coerce types and median-impute numeric NaNs using *this frame's* medians.

    Note: prefer :class:`Preprocessor` for the train/serve path; this helper imputes
    on whatever frame it is given and is only safe when used on a single fixed frame.
    """
    out = coerce_numeric(df)
    for col in NUMERIC:
        if col in out.columns:
            median = out[col].median()
            out[col] = out[col].fillna(median if not np.isnan(median) else 0.0)
    return out


def encode_categoricals(df: pd.DataFrame, *, algo: str = "xgboost") -> pd.DataFrame:
    """One-hot encode raw categoricals. For CatBoost, return raw (handled natively)."""
    if algo == "catboost":
        return df.copy()
    cols = [c for c in CATEGORICAL if c in df.columns]
    return pd.get_dummies(df, columns=cols, drop_first=True)


def split(df: pd.DataFrame, *, test_size: float = 0.2, stratify: bool = True) -> SplitData:
    """Stratified train/test split on the churn target (operates on raw columns)."""
    if TARGET not in df.columns:
        raise PreprocessingError(f"Target column {TARGET!r} not present")

    y = df[TARGET].astype(int)
    X = df.drop(columns=[c for c in (TARGET, ID_COLUMN) if c in df.columns])

    strat = y if stratify else None
    X_train, X_test, y_train, y_test = train_test_split(
        X,
        y,
        test_size=test_size,
        random_state=settings.random_seed,
        stratify=strat,
    )
    logger.info(
        "Split: train=%d test=%d positive_rate_train=%.3f",
        len(X_train),
        len(X_test),
        float(np.mean(y_train)),
    )
    return SplitData(X_train, X_test, y_train, y_test)


# ---------------------------------------------------------------------------
# Fitted preprocessor — the single source of truth for the train/serve path
# ---------------------------------------------------------------------------
@dataclass
class Preprocessor:
    """Leakage-safe, serializable preprocessing pipeline.

    Learns numeric medians and the one-hot feature schema on ``fit`` (training data
    only) and reproduces exactly the same transform at serving time. Persisted
    inside the model artifact bundle.
    """

    numeric_medians: dict[str, float] = field(default_factory=dict)
    feature_columns: list[str] = field(default_factory=list)
    fitted: bool = False

    # -- internal steps -----------------------------------------------------
    def _prepare(self, X: pd.DataFrame) -> pd.DataFrame:
        """Drop id/target, coerce numeric types, and engineer features."""
        df = X.drop(
            columns=[c for c in (TARGET, ID_COLUMN) if c in X.columns],
            errors="ignore",
        ).copy()
        df = coerce_numeric(df)
        df = build_features(df)
        return df

    def _impute_and_encode(self, df: pd.DataFrame) -> pd.DataFrame:
        out = df.copy()

        numeric_cols = out.select_dtypes(include="number").columns.tolist()
        for col in numeric_cols:
            median = self.numeric_medians.get(col)
            if median is None:  # fit-time fallback
                median = float(out[col].median()) if out[col].notna().any() else 0.0
            out[col] = out[col].fillna(median)

        object_cols = out.select_dtypes(include=["object", "category"]).columns.tolist()
        out = pd.get_dummies(out, columns=object_cols, dummy_na=False)

        # Models expect numeric input — cast one-hot booleans to int.
        bool_cols = out.select_dtypes(include="bool").columns.tolist()
        if bool_cols:
            out[bool_cols] = out[bool_cols].astype(int)
        return out

    # -- public API ---------------------------------------------------------
    def fit(self, X: pd.DataFrame) -> Preprocessor:
        prepared = self._prepare(X)
        for col in prepared.select_dtypes(include="number").columns:
            series = prepared[col]
            self.numeric_medians[col] = float(series.median()) if series.notna().any() else 0.0
        encoded = self._impute_and_encode(prepared)
        self.feature_columns = list(encoded.columns)
        self.fitted = True
        logger.info("Fitted preprocessor: %d feature columns", len(self.feature_columns))
        return self

    def transform(self, X: pd.DataFrame) -> pd.DataFrame:
        if not self.fitted:
            raise PreprocessingError("Preprocessor must be fitted before transform()")
        encoded = self._impute_and_encode(self._prepare(X))

        # Align to the training schema: add missing one-hot columns as 0, drop extras,
        # and enforce the exact training column order.
        for col in self.feature_columns:
            if col not in encoded.columns:
                encoded[col] = 0
        return encoded[self.feature_columns]

    def fit_transform(self, X: pd.DataFrame) -> pd.DataFrame:
        return self.fit(X).transform(X)
