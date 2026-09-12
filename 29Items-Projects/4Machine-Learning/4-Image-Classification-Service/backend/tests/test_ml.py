"""Tests for the ML pipeline: model forward, metrics, dataset, threshold calibration.

Uses the small architecture and synthetic data so it runs fast and offline.
"""

from __future__ import annotations

import numpy as np
import torch
from app.ml.dataset import DEFAULT_LABELS, SyntheticProductDataset, build_dataloaders
from app.ml.metrics import calibrate_thresholds, macro_f1, sigmoid
from app.ml.model import ViTConfig, ViTMultiLabelClassifier


def test_model_forward_shape():
    model = ViTMultiLabelClassifier(num_labels=8, config=ViTConfig.small())
    out = model(torch.randn(2, 3, 224, 224))
    assert out.shape == (2, 8)


def test_freeze_backbone_only_head_trainable():
    model = ViTMultiLabelClassifier(num_labels=4, config=ViTConfig.small())
    model.freeze_backbone()
    trainable = {n for n, p in model.named_parameters() if p.requires_grad}
    assert trainable == {"head.weight", "head.bias"}


def test_macro_f1_perfect():
    y = np.array([[1, 0], [0, 1]])
    assert macro_f1(y, y) == 1.0


def test_sigmoid_range():
    out = sigmoid(np.array([-100.0, 0.0, 100.0]))
    assert out[0] < 1e-3 and abs(out[1] - 0.5) < 1e-6 and out[2] > 0.999


def test_calibrate_thresholds_shape():
    rng = np.random.default_rng(0)
    y = rng.integers(0, 2, size=(50, 4))
    scores = rng.random((50, 4))
    thresholds = calibrate_thresholds(y, scores)
    assert len(thresholds) == 4
    assert all(0.0 <= t <= 1.0 for t in thresholds)


def test_synthetic_dataset_item_shapes():
    ds = SyntheticProductDataset(size=4, label_vocab=DEFAULT_LABELS)
    image, target = ds[0]
    assert image.shape == (3, 224, 224)
    assert target.shape == (len(DEFAULT_LABELS),)
    assert target.sum() >= 1  # at least one active label


def test_build_dataloaders_synthetic():
    train_dl, val_dl = build_dataloaders(
        DEFAULT_LABELS, batch_size=8, synthetic_train=16, synthetic_val=8
    )
    images, targets = next(iter(train_dl))
    assert images.shape[1:] == (3, 224, 224)
    assert targets.shape[1] == len(DEFAULT_LABELS)
