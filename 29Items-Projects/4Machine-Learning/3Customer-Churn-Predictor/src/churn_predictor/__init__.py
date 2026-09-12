"""Customer Churn Predictor — core package.

Houses all domain logic (data access, feature engineering, model training/serving,
SHAP explanations, and retention recommendations). The Streamlit app in ``app/``
is a thin client over this package.
"""

from __future__ import annotations

__version__ = "0.1.0"


class ChurnError(Exception):
    """Base class for all application-specific errors."""


class ConfigError(ChurnError):
    """Raised when configuration is missing or invalid."""


class DataLoadError(ChurnError):
    """Raised when a data source is unreachable or its schema is unexpected."""


class PreprocessingError(ChurnError):
    """Raised on bad or unexpected input data during preprocessing."""


class ModelArtifactError(ChurnError):
    """Raised when a model artifact is missing or version-incompatible."""


class PredictionError(ChurnError):
    """Raised when scoring fails."""


__all__ = [
    "__version__",
    "ChurnError",
    "ConfigError",
    "DataLoadError",
    "PreprocessingError",
    "ModelArtifactError",
    "PredictionError",
]
