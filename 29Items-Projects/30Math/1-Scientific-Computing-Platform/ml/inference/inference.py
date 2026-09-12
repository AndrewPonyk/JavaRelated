"""SageMaker inference handlers for the equation-pattern recognizer endpoint.

The sklearn serving container discovers these four functions by name.
Request contract (must match backend/app/services/sagemaker_client.py):

    POST application/json  {"features": {"op_count": 3.0, ...}}
    →                      {"label": "quadratic", "confidence": 0.93}

Feature vectorization order comes from the artifact's metadata via
``sciengine.ml.model`` — the skew guard rejects artifacts trained against a
different feature set.
"""

from __future__ import annotations

import json
from typing import Any


def model_fn(model_dir: str) -> Any:
    from sciengine.ml.model import load_model

    return load_model(model_dir)


def input_fn(request_body: str, content_type: str) -> dict[str, float]:
    if content_type != "application/json":
        raise ValueError(f"Unsupported content type: {content_type}")
    payload = json.loads(request_body)
    features = payload.get("features")
    if not isinstance(features, dict):
        raise ValueError("Body must be {'features': {<name>: <float>, ...}}")
    return {key: float(value) for key, value in features.items()}


def predict_fn(features: dict[str, float], model: Any) -> dict[str, Any]:
    import numpy as np

    from sciengine.ml.model import vectorize_features

    row = vectorize_features(features)
    probabilities = model.pipeline.predict_proba(row)[0]
    best = int(np.argmax(probabilities))
    return {"label": model.classes[best], "confidence": float(probabilities[best])}


def output_fn(prediction: dict[str, Any], accept: str) -> str:
    return json.dumps(prediction)
