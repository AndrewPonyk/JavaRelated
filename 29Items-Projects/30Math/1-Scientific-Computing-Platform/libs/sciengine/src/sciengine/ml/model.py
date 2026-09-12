"""Baseline pattern-recognition model (sklearn) with a stable artifact format.

Lives in sciengine so SageMaker training (`ml/training/train.py`) and any
local consumer share one implementation — the training/serving-skew guard
from docs/TECH-NOTES.md §3.6. Requires the ``sciengine[ml]`` extra
(scikit-learn + joblib).
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Literal

import numpy as np

from sciengine.ml.features import PatternLabel, PatternPrediction, expression_features

#: Canonical feature column order. Models are trained against this exact list;
#: keep it append-only and in sync with expression_features().
FEATURE_NAMES: list[str] = [
    "op_count",
    "node_count",
    "depth",
    "n_symbols",
    "n_trig",
    "n_exp",
    "n_log",
    "is_polynomial",
    "poly_degree",
]

_MODEL_FILE = "model.joblib"
_METADATA_FILE = "metadata.json"


@dataclass
class TrainedModel:
    pipeline: Any  # fitted sklearn Pipeline
    feature_names: list[str]
    accuracy: float  # held-out validation accuracy
    classes: list[str]


def features_matrix(expressions: list[str]) -> np.ndarray:
    """Vectorize expressions in the canonical FEATURE_NAMES order."""
    rows = []
    for text in expressions:
        features = expression_features(text)
        rows.append([features[name] for name in FEATURE_NAMES])
    return np.asarray(rows, dtype=float)


def vectorize_features(features: dict[str, float]) -> np.ndarray:
    """One pre-computed feature dict → model input row (serving path)."""
    missing = [name for name in FEATURE_NAMES if name not in features]
    if missing:
        raise ValueError(f"Missing feature(s): {', '.join(missing)}")
    return np.asarray([[float(features[name]) for name in FEATURE_NAMES]])


def train_baseline(
    expressions: list[str],
    labels: list[str],
    *,
    c: float = 1.0,
    max_iter: int = 2000,
    validation_fraction: float = 0.2,
    seed: int = 0,
) -> TrainedModel:
    """Fit the scaler+logistic-regression baseline and report held-out accuracy."""
    from sklearn.linear_model import LogisticRegression
    from sklearn.metrics import accuracy_score
    from sklearn.model_selection import train_test_split
    from sklearn.pipeline import Pipeline
    from sklearn.preprocessing import StandardScaler

    if len(expressions) != len(labels) or len(expressions) < 20:
        raise ValueError("Need matching expressions/labels lists with at least 20 rows.")

    matrix = features_matrix(expressions)
    y = np.asarray(labels)
    x_train, x_val, y_train, y_val = train_test_split(
        matrix, y, test_size=validation_fraction, random_state=seed, stratify=y
    )

    pipeline = Pipeline(
        [
            ("scale", StandardScaler()),
            ("clf", LogisticRegression(C=c, max_iter=max_iter)),
        ]
    )
    pipeline.fit(x_train, y_train)
    accuracy = float(accuracy_score(y_val, pipeline.predict(x_val)))

    return TrainedModel(
        pipeline=pipeline,
        feature_names=list(FEATURE_NAMES),
        accuracy=accuracy,
        classes=[str(cls) for cls in pipeline.classes_],
    )


def predict(
    model: TrainedModel,
    expression: str,
    *,
    source: Literal["heuristic", "sagemaker"] = "heuristic",
) -> PatternPrediction:
    """Classify one expression with a trained model."""
    row = features_matrix([expression])
    probabilities = model.pipeline.predict_proba(row)[0]
    best = int(np.argmax(probabilities))
    return PatternPrediction(
        label=PatternLabel(model.classes[best]),
        confidence=float(probabilities[best]),
        source=source,
    )


def save_model(model: TrainedModel, directory: str | Path) -> Path:
    """Persist pipeline + metadata. The feature order ships WITH the artifact."""
    import joblib

    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    joblib.dump(model.pipeline, directory / _MODEL_FILE)
    (directory / _METADATA_FILE).write_text(
        json.dumps(
            {
                "feature_names": model.feature_names,
                "classes": model.classes,
                "accuracy": model.accuracy,
                "algorithm": "standard-scaler + logistic-regression",
                "feature_source": "sciengine.ml.features.expression_features",
            },
            indent=2,
        )
    )
    return directory


def load_model(directory: str | Path) -> TrainedModel:
    import joblib

    directory = Path(directory)
    metadata = json.loads((directory / _METADATA_FILE).read_text())
    if metadata["feature_names"] != FEATURE_NAMES:
        raise ValueError(
            "Model artifact was trained with a different feature order than this "
            "sciengine version provides — retrain before serving (skew guard)."
        )
    return TrainedModel(
        pipeline=joblib.load(directory / _MODEL_FILE),
        feature_names=list(metadata["feature_names"]),
        accuracy=float(metadata["accuracy"]),
        classes=list(metadata["classes"]),
    )
