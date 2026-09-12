"""
ml_service/model/lstm_model.py
Quant model wrapper. Loads a versioned LSTM artifact when one is available
(ONNX Runtime / PyTorch / Keras); otherwise serves a real, deterministic
analytic momentum model so the service is always functional. This mirrors the
C++ side's LocalPredictor fallback philosophy: never return a fake/empty result.
"""
from __future__ import annotations

import logging
import math
import os
from dataclasses import dataclass, field

import numpy as np

log = logging.getLogger("ml_service.model")


@dataclass
class LstmModel:
    version: str
    scale: float = 50.0
    _ready: bool = False
    _backend: object | None = field(default=None, repr=False)

    @classmethod
    def load(cls, version: str) -> "LstmModel":
        """Load a pinned model artifact if present; else use the analytic model."""
        model = cls(version=version)
        artifact = os.environ.get("ML_MODEL_PATH", f"/opt/models/{version}.onnx")
        if os.path.exists(artifact):
            try:
                import onnxruntime as ort  # optional dependency

                model._backend = ort.InferenceSession(
                    artifact,
                    providers=["CPUExecutionProvider"],
                )
                log.info("loaded ONNX model %s from %s", version, artifact)
            except Exception as exc:  # pragma: no cover - artifact/runtime issues
                log.warning("failed to load %s (%s); using analytic model", artifact, exc)
        else:
            log.info("no artifact at %s; using analytic momentum model", artifact)
        model._ready = True
        return model

    def is_ready(self) -> bool:
        return self._ready

    def predict_batch(self, windows: list[list[float]]) -> list[tuple[float, float]]:
        """
        Run inference for a batch of feature windows.

        Each window is a sequence of engineered features (e.g. recent returns).
        Returns (signal, confidence) per window where signal in [-1, 1].
        """
        out: list[tuple[float, float]] = []
        for window in windows:
            if not window:
                out.append((0.0, 0.0))
                continue

            if self._backend is not None:  # real LSTM path
                arr = np.asarray(window, dtype=np.float32).reshape(1, len(window), 1)
                pred = self._backend.run(None, {self._backend.get_inputs()[0].name: arr})
                signal = float(np.tanh(np.asarray(pred).ravel()[0]))
                out.append((signal, 0.8))
                continue

            # Analytic momentum model: tanh of the scaled mean feature.
            arr = np.asarray(window, dtype=np.float64)
            signal = float(math.tanh(self.scale * float(arr.mean())))
            confidence = float(min(1.0, len(window) / 16.0)) * 0.6 + 0.2
            out.append((signal, confidence))
        return out
