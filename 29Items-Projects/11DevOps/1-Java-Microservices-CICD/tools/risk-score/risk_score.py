#!/usr/bin/env python3
"""Deployment risk score — estimates the probability that a change set causes a
failed/rolled-back deployment, from code-change metrics.

Used by:
  * ci.yml            — advisory score on every PR (job summary)
  * cd-production.yml — blocking gate (--fail-above 0.70) before promotion

Model strategy (progressive enhancement):
  1. If a trained model exists (model.joblib, produced by train_model.py) and
     scikit-learn is importable, use LogisticRegression.predict_proba.
  2. Otherwise fall back to a transparent hand-tuned logistic heuristic with the
     SAME feature vector, so the CI contract never breaks. The weights below were
     seeded from industry priors (change size, config/schema risk, test signal)
     and are meant to be replaced by trained coefficients (see README.md).

Stdlib-only in fallback mode — no pip install needed on CI runners.

Examples:
  python risk_score.py --base origin/main --head HEAD
  python risk_score.py --base <sha> --head <sha> --coverage-xml target/site/jacoco/jacoco.xml
  python risk_score.py --demo --output json
"""

from __future__ import annotations

import argparse
import json
import math
import subprocess
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, asdict
from pathlib import Path

MODEL_PATH = Path(__file__).parent / "model.joblib"

# Paths that historically correlate with deployment risk in this repo layout.
MIGRATION_MARKERS = ("db/migration/",)
CI_MARKERS = (".github/workflows/",)
HELM_MARKERS = ("deploy/helm/", "deploy/argocd/")
BUILD_MARKERS = ("pom.xml", "Dockerfile")
TEST_MARKERS = ("/src/test/", "/tests/")


@dataclass
class Features:
    lines_added: int
    lines_deleted: int
    files_changed: int
    touches_migrations: bool
    touches_ci: bool
    touches_helm: bool
    touches_build: bool
    test_ratio: float          # test lines changed / total lines changed
    coverage: float | None     # measured line coverage 0..1, None if unknown


def run_git(repo: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(repo), *args],
        capture_output=True, text=True, check=True,
    )
    return result.stdout


def collect_features(repo: Path, base: str, head: str, coverage_xml: Path | None) -> Features:
    # three-dot diff = changes since the merge base, i.e. "what this branch adds"
    numstat = run_git(repo, "diff", "--numstat", f"{base}...{head}")

    added = deleted = files = test_lines = 0
    markers = {"mig": False, "ci": False, "helm": False, "build": False}
    for line in numstat.splitlines():
        parts = line.split("\t")
        if len(parts) != 3:
            continue
        a, d, path = parts
        files += 1
        # binary files report "-"
        a_i = int(a) if a.isdigit() else 0
        d_i = int(d) if d.isdigit() else 0
        added += a_i
        deleted += d_i
        norm = path.replace("\\", "/")
        if any(m in norm for m in MIGRATION_MARKERS):
            markers["mig"] = True
        if any(m in norm for m in CI_MARKERS):
            markers["ci"] = True
        if any(m in norm for m in HELM_MARKERS):
            markers["helm"] = True
        if any(norm.endswith(m) or f"/{m}" in norm for m in BUILD_MARKERS):
            markers["build"] = True
        if any(m in norm for m in TEST_MARKERS):
            test_lines += a_i + d_i

    total = added + deleted
    return Features(
        lines_added=added,
        lines_deleted=deleted,
        files_changed=files,
        touches_migrations=markers["mig"],
        touches_ci=markers["ci"],
        touches_helm=markers["helm"],
        touches_build=markers["build"],
        test_ratio=round(test_lines / total, 3) if total else 0.0,
        coverage=read_line_coverage(coverage_xml) if coverage_xml else None,
    )


def read_line_coverage(jacoco_xml: Path) -> float | None:
    """Extract overall LINE coverage from a JaCoCo XML report."""
    try:
        root = ET.parse(jacoco_xml).getroot()
        for counter in root.findall("counter"):
            if counter.get("type") == "LINE":
                missed = int(counter.get("missed", 0))
                covered = int(counter.get("covered", 0))
                return round(covered / (missed + covered), 4) if (missed + covered) else None
    except (OSError, ET.ParseError):
        return None
    return None


def heuristic_probability(f: Features) -> float:
    """Transparent logistic fallback. Positive weight = more risk."""
    size_factor = min((f.lines_added + f.lines_deleted) / 800.0, 1.5)
    files_factor = min(f.files_changed / 25.0, 1.5)
    missing_tests = max(0.0, 0.30 - min(f.test_ratio, 0.30))          # 0 .. 0.30
    coverage_gap = max(0.0, 0.80 - f.coverage) if f.coverage is not None else 0.0

    z = (
        -2.0
        + 1.1 * size_factor
        + 0.6 * files_factor
        + 1.3 * float(f.touches_migrations)   # schema changes: hardest to roll back
        + 0.7 * float(f.touches_helm)         # deploy-config changes bite at deploy time
        + 0.5 * float(f.touches_ci)
        + 0.4 * float(f.touches_build)
        + 3.0 * missing_tests                 # large change with little test delta
        + 2.5 * coverage_gap
    )
    return 1.0 / (1.0 + math.exp(-z))


def model_probability(f: Features) -> tuple[float, str]:
    """Trained model if available, heuristic otherwise. Returns (probability, source)."""
    if MODEL_PATH.exists():
        try:
            import joblib  # optional dependency

            model = joblib.load(MODEL_PATH)
            vector = [[
                f.lines_added + f.lines_deleted,
                f.files_changed,
                float(f.touches_migrations),
                float(f.touches_ci),
                float(f.touches_helm),
                float(f.touches_build),
                f.test_ratio,
                f.coverage if f.coverage is not None else 0.80,
            ]]
            return float(model.predict_proba(vector)[0][1]), "trained-model"
        except Exception as exc:  # noqa: BLE001 - degrade gracefully in CI
            print(f"warning: could not use {MODEL_PATH.name} ({exc}); using heuristic", file=sys.stderr)
    return heuristic_probability(f), "heuristic"


def bucket(score: float) -> tuple[str, str]:
    if score < 0.40:
        return "LOW", "Standard rolling deployment."
    if score < 0.70:
        return "MEDIUM", "Rolling deployment; watch dashboards for 15 minutes after rollout."
    return "HIGH", "Use blue-green (blueGreen.enabled=true), extra reviewer, and a rollback plan."


def demo_features() -> Features:
    return Features(
        lines_added=640, lines_deleted=120, files_changed=18,
        touches_migrations=True, touches_ci=False, touches_helm=True,
        touches_build=False, test_ratio=0.18, coverage=0.83,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Deployment risk score from code metrics")
    parser.add_argument("--base", help="base git ref (e.g. origin/main or the currently deployed SHA)")
    parser.add_argument("--head", default="HEAD", help="head git ref (default: HEAD)")
    parser.add_argument("--repo-dir", default=".", help="repository root (default: cwd)")
    parser.add_argument("--coverage-xml", type=Path, help="optional JaCoCo XML report for the coverage feature")
    parser.add_argument("--output", choices=["text", "json"], default="text")
    parser.add_argument("--fail-above", type=float, help="exit 2 when score >= this threshold (prod gate)")
    parser.add_argument("--demo", action="store_true", help="score canned features (no git needed)")
    args = parser.parse_args()

    if args.demo:
        features = demo_features()
        base, head = "(demo)", "(demo)"
    else:
        if not args.base:
            parser.error("--base is required unless --demo is used")
        try:
            features = collect_features(Path(args.repo_dir), args.base, args.head, args.coverage_xml)
        except subprocess.CalledProcessError as exc:
            print(f"error: git diff failed: {exc.stderr.strip()}", file=sys.stderr)
            return 1
        base, head = args.base, args.head

    score, source = model_probability(features)
    level, recommendation = bucket(score)

    payload = {
        "base": base,
        "head": head,
        "score": round(score, 4),
        "level": level,
        "model": source,
        "recommendation": recommendation,
        "features": asdict(features),
    }

    if args.output == "json":
        print(json.dumps(payload, indent=2))
    else:
        f = features
        print(f"Deployment risk: {score:.2f}  [{level}]  (model: {source})")
        print(f"  range           : {base} -> {head}")
        print(f"  lines +/-       : +{f.lines_added} / -{f.lines_deleted} across {f.files_changed} file(s)")
        print(f"  migrations      : {f.touches_migrations}   helm/argo: {f.touches_helm}   "
              f"ci: {f.touches_ci}   build: {f.touches_build}")
        print(f"  test ratio      : {f.test_ratio:.2f}"
              + (f"   coverage: {f.coverage:.1%}" if f.coverage is not None else "   coverage: n/a"))
        print(f"  recommendation  : {recommendation}")

    if args.fail_above is not None and score >= args.fail_above:
        print(f"\nRISK GATE FAILED: {score:.2f} >= {args.fail_above:.2f} "
              f"(re-run with override, or split/de-risk the release)", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
