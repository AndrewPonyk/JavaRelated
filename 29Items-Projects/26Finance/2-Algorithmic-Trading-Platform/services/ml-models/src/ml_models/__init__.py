"""ml-models — price-prediction training and online inference.

The default served model is the NumPy :class:`MomentumLogisticPredictor`. The
torch Transformer lives in ``ml_models.transformers`` and is imported lazily so
this package is usable without torch installed.
"""

from ml_models.inference import InferenceService
from ml_models.predictor import MomentumLogisticPredictor, featurize

__all__ = ["InferenceService", "MomentumLogisticPredictor", "featurize"]
