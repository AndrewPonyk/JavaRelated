"""ONNX Runtime inference service.

The model and labels are loaded ONCE (at app startup) and reused for every request.
The ``InferenceSession`` is injected so tests can substitute a fake session without
touching ONNX or downloading any model.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Protocol

import numpy as np

from app.core.config import Settings
from app.core.logging import get_logger
from app.models.schemas import LabelPrediction

logger = get_logger(__name__)


class InferenceError(RuntimeError):
    """Raised when a forward pass fails."""


class OnnxSession(Protocol):
    """Minimal protocol matching onnxruntime.InferenceSession (eases testing)."""

    def run(self, output_names, input_feed) -> list[np.ndarray]: ...


def _sigmoid(x: np.ndarray) -> np.ndarray:
    return 1.0 / (1.0 + np.exp(-x))


class InferenceService:
    """Wraps an ONNX session + label vocabulary + per-label thresholds."""

    def __init__(
        self,
        session: OnnxSession,
        labels: list[str],
        thresholds: list[float],
        model_version: str,
        input_name: str = "pixel_values",
    ) -> None:
        self.session = session
        self.labels = labels
        self.thresholds = np.asarray(thresholds, dtype=np.float32)
        self.model_version = model_version
        self.input_name = input_name

    @classmethod
    def from_settings(cls, settings: Settings) -> InferenceService:
        """Build a service from the ONNX model + label/threshold artifacts on disk.

        ``onnxruntime`` is imported lazily so unit tests that inject a fake session
        don't require it installed.
        """
        import onnxruntime as ort  # local import: keep import-time light for tests

        labels = json.loads(Path(settings.labels_path).read_text())
        thresholds = json.loads(Path(settings.thresholds_path).read_text())
        session = ort.InferenceSession(settings.model_path, providers=["CPUExecutionProvider"])
        return cls(
            session=session,
            labels=labels,
            thresholds=thresholds,
            model_version=settings.model_version,
        )

    def _run(self, tensor: np.ndarray) -> np.ndarray:
        try:
            outputs = self.session.run(None, {self.input_name: tensor})
        except Exception as exc:  # noqa: BLE001
            raise InferenceError("Model forward pass failed") from exc
        return _sigmoid(np.asarray(outputs[0]))  # (batch, num_labels)

    def _to_predictions(self, scores: np.ndarray, top_k: int) -> list[LabelPrediction]:
        predictions = [
            LabelPrediction(name=self.labels[i], score=float(scores[i]))
            for i in range(len(self.labels))
            if scores[i] >= self.thresholds[i]
        ]
        predictions.sort(key=lambda p: p.score, reverse=True)
        return predictions[:top_k]

    def predict(self, tensor: np.ndarray, top_k: int = 10) -> list[LabelPrediction]:
        """Run a forward pass on a single image (NCHW with N=1).

        Multi-label => sigmoid + per-label thresholds (NOT softmax/argmax).
        """
        scores = self._run(tensor)[0]
        return self._to_predictions(scores, top_k)

    def predict_batch(self, tensor: np.ndarray, top_k: int = 10) -> list[list[LabelPrediction]]:
        """Run a single batched forward pass over a stacked NCHW tensor (N>=1)."""
        scores = self._run(tensor)
        return [self._to_predictions(row, top_k) for row in scores]
