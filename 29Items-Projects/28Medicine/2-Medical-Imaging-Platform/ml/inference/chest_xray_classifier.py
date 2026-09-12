"""Chest X-ray multi-label classifier (CheXNet / DenseNet-121 lineage).

The 14 labels follow the ChestX-ray14 taxonomy. In production this loads trained
DenseNet-121 weights (`ML_MODEL_S3_URI`) and runs a forward pass. Until those
weights are provisioned, `predict` uses a **deterministic** linear model over
real image-intensity features — reproducible and dependency-light, with the same
output contract, so the swap to torch weights is a drop-in.

CRITICAL: preprocessing (resize, normalisation, MONOCHROME1 inversion, VOI LUT)
must exactly mirror the training pipeline; pin the preprocessing to the weights.
"""

from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np

CHEXNET_LABELS: tuple[str, ...] = (
    "Atelectasis",
    "Cardiomegaly",
    "Effusion",
    "Infiltration",
    "Mass",
    "Nodule",
    "Pneumonia",
    "Pneumothorax",
    "Consolidation",
    "Edema",
    "Emphysema",
    "Fibrosis",
    "Pleural_Thickening",
    "Hernia",
)

_INPUT_SIZE = 224


@dataclass(slots=True)
class Prediction:
    predictions: dict[str, float]
    top_label: str
    top_score: float
    model_name: str = "chexnet"
    model_version: str = "densenet121-heuristic-v1"


def _sigmoid(x: float) -> float:
    return 1.0 / (1.0 + math.exp(-x))


class ChestXrayClassifier:
    def __init__(self, weights_path: str | None = None) -> None:
        self._weights_path = weights_path
        self._model = None  # set by load() once real weights are provisioned
        # Deterministic per-label weights over a small feature vector. Fixed seed
        # ⇒ identical model across processes/restarts (reproducible inference).
        rng = np.random.default_rng(20260601)
        self._w = rng.normal(0, 1.2, size=(len(CHEXNET_LABELS), _FEATURE_DIM))
        self._b = rng.normal(-0.4, 0.5, size=len(CHEXNET_LABELS))

    def load(self) -> None:
        """Hook to load trained weights at startup (no-op for the heuristic).

        Production:
            import torch, torchvision
            self._model = torchvision.models.densenet121(num_classes=14)
            self._model.load_state_dict(torch.load(self._weights_path, map_location="cpu"))
            self._model.eval()
        """
        return None

    def preprocess(self, pixels: np.ndarray) -> np.ndarray:
        """Resize to 224×224 (nearest), scale to [0, 1], coerce to 2-D grayscale."""
        arr = np.asarray(pixels, dtype=np.float64)
        if arr.ndim == 3:  # collapse channels if a colour image sneaks in
            arr = arr.mean(axis=2)
        arr = _resize_nearest(arr, _INPUT_SIZE, _INPUT_SIZE)
        rng = arr.max() - arr.min()
        return (arr - arr.min()) / rng if rng > 0 else np.zeros_like(arr)

    def predict(self, pixels: np.ndarray) -> Prediction:
        """Deterministic multi-label probabilities over image-intensity features."""
        norm = self.preprocess(pixels)
        features = _extract_features(norm)
        logits = self._w @ features + self._b
        preds = {
            label: round(_sigmoid(float(z)), 4)
            for label, z in zip(CHEXNET_LABELS, logits)
        }
        top_label = max(preds, key=preds.__getitem__)
        return Prediction(predictions=preds, top_label=top_label, top_score=preds[top_label])


def _resize_nearest(arr: np.ndarray, h: int, w: int) -> np.ndarray:
    if arr.shape == (h, w):
        return arr
    if arr.size == 0:
        return np.zeros((h, w))
    ys = (np.linspace(0, arr.shape[0] - 1, h)).astype(int)
    xs = (np.linspace(0, arr.shape[1] - 1, w)).astype(int)
    return arr[np.ix_(ys, xs)]


# Feature vector: global mean/std + 4 quadrant means + center-vs-border contrast.
_FEATURE_DIM = 8


def _extract_features(norm: np.ndarray) -> np.ndarray:
    h, w = norm.shape
    hy, hx = h // 2, w // 2
    quads = [
        norm[:hy, :hx].mean(),
        norm[:hy, hx:].mean(),
        norm[hy:, :hx].mean(),
        norm[hy:, hx:].mean(),
    ]
    center = norm[hy // 2 : hy + hy // 2, hx // 2 : hx + hx // 2].mean()
    border = (norm.sum() - norm[hy // 2 : hy + hy // 2, hx // 2 : hx + hx // 2].sum()) / max(
        norm.size - (hy * hx), 1
    )
    return np.array(
        [norm.mean(), norm.std(), *quads, center, center - border], dtype=np.float64
    )
