"""Offline training entrypoint for the severity-prediction model.

Pipeline: fetch labeled pairs from Neo4j -> features -> train classifier ->
evaluate -> export joblib artifact (local path or s3://...).

Run:
    python -m training.train --output s3://dic-models/severity/$(date +%Y%m%d)
    python -m training.train --output ./artifacts/severity.joblib
"""
from __future__ import annotations

import argparse
import os
import tempfile
from urllib.parse import urlparse

import joblib
from sklearn.dummy import DummyClassifier
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.metrics import classification_report
from sklearn.model_selection import train_test_split

from training.dataset import fetch_labeled_pairs
from training.graph_features import SEVERITY_CLASSES, compute_node_embeddings

try:  # boto3 is only needed for s3:// outputs
    import boto3
except ImportError:  # pragma: no cover
    boto3 = None


def _save_artifact(model, output: str) -> None:
    parsed = urlparse(output)
    if parsed.scheme == "s3":
        if boto3 is None:
            raise RuntimeError("boto3 is required to write s3:// artifacts")
        with tempfile.NamedTemporaryFile(suffix=".joblib", delete=False) as tmp:
            joblib.dump(model, tmp.name)
            local = tmp.name
        boto3.client("s3").upload_file(local, parsed.netloc, parsed.path.lstrip("/"))
        os.unlink(local)
        print(f"Uploaded artifact -> {output}")
    else:
        os.makedirs(os.path.dirname(os.path.abspath(output)) or ".", exist_ok=True)
        joblib.dump(model, output)
        print(f"Saved artifact -> {output}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Train drug-interaction severity model")
    parser.add_argument("--neo4j-uri", default="bolt://localhost:7687")
    parser.add_argument("--neo4j-user", default="neo4j")
    parser.add_argument("--neo4j-password", default="password")
    parser.add_argument("--output", required=True, help="Artifact output URI (s3:// or path)")
    parser.add_argument("--test-size", type=float, default=0.2)
    parser.add_argument("--random-state", type=int, default=42)
    parser.add_argument(
        "--compute-embeddings",
        action="store_true",
        help="Recompute node embeddings via Neo4j GDS before training",
    )
    args = parser.parse_args()

    if args.compute_embeddings:
        from neo4j import GraphDatabase

        driver = GraphDatabase.driver(
            args.neo4j_uri, auth=(args.neo4j_user, args.neo4j_password)
        )
        try:
            compute_node_embeddings(driver)
            print("Recomputed node embeddings.")
        finally:
            driver.close()

    pairs = fetch_labeled_pairs(args.neo4j_uri, args.neo4j_user, args.neo4j_password)
    if not pairs:
        raise SystemExit("No labeled pairs found. Seed curated interactions first.")

    X = [p.features for p in pairs]
    y = [SEVERITY_CLASSES.index(p.severity) for p in pairs]
    print(f"Loaded {len(pairs)} labeled pairs across {len(set(y))} classes.")

    # Gradient boosting needs enough samples/classes; fall back gracefully.
    if len(pairs) < 20 or len(set(y)) < 2:
        print("WARNING: tiny/degenerate dataset -> training a DummyClassifier baseline.")
        model = DummyClassifier(strategy="most_frequent").fit(X, y)
        _save_artifact(model, args.output)
        return

    X_tr, X_te, y_tr, y_te = train_test_split(
        X, y, test_size=args.test_size, stratify=y, random_state=args.random_state
    )
    model = GradientBoostingClassifier(random_state=args.random_state).fit(X_tr, y_tr)

    print("\n=== Evaluation ===")
    print(
        classification_report(
            y_te,
            model.predict(X_te),
            labels=list(range(len(SEVERITY_CLASSES))),
            target_names=SEVERITY_CLASSES,
            zero_division=0,
        )
    )
    _save_artifact(model, args.output)


if __name__ == "__main__":
    main()
