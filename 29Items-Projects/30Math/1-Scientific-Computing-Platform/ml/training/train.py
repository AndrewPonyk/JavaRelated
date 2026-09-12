"""SageMaker training entrypoint for the equation-pattern recognizer.

SageMaker contract: hyperparameters arrive as CLI args, data channels and the
output location as SM_* environment variables. Runs identically on a laptop:

    python ml/training/train.py --model-dir /tmp/model            # synthetic corpus
    python ml/training/train.py --train-data data/train.csv ...   # curated corpus

The actual learning logic lives in ``sciengine.ml.model`` so the API fallback,
this script, and the serving container can never drift apart
(docs/TECH-NOTES.md §3.6, training/serving skew).
"""

from __future__ import annotations

import argparse
import csv
import json
import os
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-dir", default=os.environ.get("SM_MODEL_DIR", "/opt/ml/model"))
    parser.add_argument(
        "--train-data",
        default=os.environ.get("SM_CHANNEL_TRAIN", ""),
        help="Directory or CSV file with columns expression,label. "
        "Empty → generate a synthetic corpus via sciengine.ml.corpus.",
    )
    parser.add_argument("--c", type=float, default=1.0, help="LogisticRegression C")
    parser.add_argument("--max-iter", type=int, default=2000)
    parser.add_argument("--n-per-label", type=int, default=300, help="synthetic corpus size")
    parser.add_argument("--seed", type=int, default=0)
    return parser.parse_args()


def load_corpus(source: str, *, n_per_label: int, seed: int) -> tuple[list[str], list[str]]:
    """CSV corpus if provided, else a balanced synthetic one (ground truth by
    construction — see sciengine.ml.corpus)."""
    if source:
        path = Path(source)
        if path.is_dir():
            candidates = sorted(path.glob("*.csv"))
            if not candidates:
                raise FileNotFoundError(f"No CSV files in training channel: {source}")
            path = candidates[0]
        expressions, labels = [], []
        with path.open(newline="", encoding="utf-8") as handle:
            for row in csv.DictReader(handle):
                expressions.append(row["expression"])
                labels.append(row["label"])
        return expressions, labels

    from sciengine.ml.corpus import generate_corpus

    return generate_corpus(n_per_label, seed=seed)


def main() -> None:
    args = parse_args()

    from sciengine.ml.model import save_model, train_baseline

    expressions, labels = load_corpus(args.train_data, n_per_label=args.n_per_label, seed=args.seed)
    model = train_baseline(expressions, labels, c=args.c, max_iter=args.max_iter, seed=args.seed)

    # SageMaker parses metrics from stdout via metric_definitions regexes.
    print(json.dumps({"metric": "validation:accuracy", "value": model.accuracy}))

    model_dir = save_model(model, args.model_dir)
    print(f"model persisted to {model_dir} (validation accuracy {model.accuracy:.3f})")


if __name__ == "__main__":
    main()
