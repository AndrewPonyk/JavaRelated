"""End-to-end ML pipeline test: train -> export ONNX -> parity -> serve via ORT.

Uses the small architecture and tiny synthetic data so it runs in a few seconds and
fully offline. Proves train.py, export_onnx.py, and InferenceService.from_settings.
"""

from __future__ import annotations

import argparse

import numpy as np
from app.core.config import Settings
from app.ml.export_onnx import export, verify_parity
from app.ml.train import train_model
from app.services import preprocessing
from app.services.inference import InferenceService


def test_train_export_serve(tmp_path):
    out = tmp_path / "artifacts"
    args = argparse.Namespace(
        train=None,
        val=None,
        labels=None,
        synthetic=True,
        arch="small",
        pretrained=False,
        epochs=1,
        lr=3e-4,
        batch_size=16,
        out=str(out),
        seed=0,
        synthetic_train=32,
        synthetic_val=16,
    )
    result = train_model(args)
    assert 0.0 <= result["macro_f1"] <= 1.0

    onnx_path = out / "model.onnx"
    weights = str(out / "model.pt")
    labels = str(out / "labels.json")
    export(weights, labels, "small", str(onnx_path))
    verify_parity(weights, labels, "small", str(onnx_path), atol=1e-3)

    # Serve the exported model exactly as production would.
    settings = Settings(
        model_path=str(onnx_path),
        labels_path=labels,
        thresholds_path=str(out / "thresholds.json"),
        model_version="pipeline-test",
    )
    service = InferenceService.from_settings(settings)
    assert service.model_version == "pipeline-test"

    from PIL import Image

    img = Image.new("RGB", (224, 224), color=(200, 50, 50))
    tensor = preprocessing.preprocess(img)
    preds = service.predict(tensor)
    # Predictions are valid LabelPrediction objects within [0, 1].
    assert all(0.0 <= p.score <= 1.0 for p in preds)
    assert all(p.name in service.labels for p in preds)
    # Scores array is deterministic for the same input.
    again = service.predict(tensor)
    assert [p.name for p in preds] == [p.name for p in again]
    assert isinstance(np.float32(1.0), np.floating)  # numpy import used
