"""Train the severity-classification model and persist the artifact.

Usage:  python -m ml.train [--out ml/severity_model.joblib]

Corpus: principled synthetic set — ZAP rule taxonomy (risk bands), confirmed
sqlmap techniques, and verified-XSS phrasing, each with randomized wording
noise. Deterministic (fixed seed) so builds are reproducible. As analyst-
labeled history accrues in `findings`, retrain on real rows (the feature
extractor is shared, so the swap is a data change only).
"""

from __future__ import annotations

import argparse
import random
from pathlib import Path

import joblib
from sklearn.calibration import CalibratedClassifierCV
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, f1_score
from sklearn.model_selection import train_test_split

from app.services.ml_features import FEATURE_NAMES, extract_features

SEVERITIES = ["info", "low", "medium", "high", "critical"]

# (band, source, rule_id, title-templates, description-fragments)
# Risk bands mirror ZAP's taxonomy + our SQLMap/XSS confirmation semantics.
_CORPUS: list[tuple[str, str, str, list[str], list[str]]] = [
    ("critical", "sqlmap", "SQLI-CONFIRMED",
     ["SQL injection in parameter {p}", "Union-based SQL injection ({p})",
      "Error-based SQL injection affecting {p}"],
     ["confirmed via sqlmap union technique", "dbms error leak, full data extraction possible",
      "time-based blind injection verified", "authentication bypass via injection"]),
    ("critical", "zap", "40014",
     ["Buffer overflow in {p}", "Remote code execution via {p}"],
     ["crash and potential code execution", "memory corruption reported by scanner"]),
    ("high", "sqlmap", "SQLI-CONFIRMED",
     ["Boolean-blind SQL injection ({p})", "Time-blind SQL injection in {p}"],
     ["confirmed boolean-based blind", "conditional responses differ", "delay-based oracle"]),
    ("high", "xss_engine", "XSS-ATTRIBUTE",
     ["Reflected XSS in attribute context ({p})", "Unescaped reflection in {p}"],
     ["payload reflected raw inside attribute", "javascript: URI executes in href context"]),
    ("high", "xss_engine", "XSS-HTML_BODY",
     ["Reflected XSS in HTML body ({p})", "Script injection via {p}"],
     ["script tags survive output encoding", "event handler attribute injected"]),
    ("high", "xss_engine", "XSS-SCRIPT",
     ["Script-context injection in {p}"],
     ["reflection inside javascript block, quotes escaped insufficiently"]),
    ("high", "zap", "40018",
     ["SQL injection in {p}", "SQL injection suspicion in {p}"],
     ["sql syntax error leakage", "database error disclosed", "classic sqli probe reflected"]),
    ("high", "zap", "40019",
     ["OS command injection in {p}"],
     ["command output observed", "shell metacharacters interpreted"]),
    ("medium", "zap", "10038",
     ["Content Security Policy header not set", "CSP missing on response"],
     ["header hardening missing", "no content-security-policy directive"]),
    ("medium", "zap", "10098",
     ["Cross-domain misconfiguration"],
     ["permissive access-control-allow-origin", "cors allows any origin"]),
    ("medium", "zap", "10021",
     ["X-Content-Type-Options header missing"],
     ["mime sniffing possible", "missing nosniff header"]),
    ("medium", "zap", "10106",
     ["HTTP only site"],
     ["site served over plain http", "transport not encrypted"]),
    ("medium", "zap", "10104",
     ["User agent fuzzer"],
     ["server leaks internals on crafted user agents"]),
    ("low", "zap", "10035",
     ["Strict-Transport-Security header missing"],
     ["hsts policy absent"]),
    ("low", "zap", "10017",
     ["Cookie without SameSite attribute"],
     ["cookie flag missing", "same-site protection absent"]),
    ("low", "zap", "10032",
     ["Directory browsing enabled"],
     ["index listing exposed"]),
    ("info", "zap", "10027",
     ["Information disclosure: debug page"],
     ["information exposure on debug endpoints", "verbose banner"]),
    ("info", "zap", "10000",
     ["In Page banner found"],
     ["technology fingerprint from banner"]),
]

_PARAMS = ["id", "q", "search", "name", "page", "cat", "user_id", "ref", "msg", "email", "url", "next"]
_NOISE = [
    "", " reported during passive scan", " (observed twice)", " on staging target",
    " with encoding variants", " via GET parameter", " in POST body",
]


def _rows(n_per_template: int = 60, seed: int = 42):
    rng = random.Random(seed)
    features, labels = [], []
    for band, source, rule_id, titles, descs in _CORPUS:
        for _ in range(n_per_template):
            title = rng.choice(titles).format(p=rng.choice(_PARAMS))
            desc = rng.choice(descs) + rng.choice(_NOISE)
            param = rng.choice(_PARAMS) if "{p}" in title or rng.random() < 0.7 else None
            method = rng.choice(["GET"] * 4 + ["POST"])
            features.append(extract_features(
                source=source, rule_id=rule_id, title=title,
                description=desc, param=param, method=method,
            ))
            labels.append(band)
    return features, labels


def train(out_path: str, n_per_template: int = 60) -> dict:
    features, labels = _rows(n_per_template)
    x_train, x_test, y_train, y_test = train_test_split(
        features, labels, test_size=0.25, random_state=42, stratify=labels
    )
    base = LogisticRegression(max_iter=2000, C=1.0, class_weight="balanced")
    model = CalibratedClassifierCV(base, method="sigmoid", cv=5)
    model.fit(x_train, y_train)

    preds = model.predict(x_test)
    macro_f1 = f1_score(y_test, preds, average="macro")
    report = classification_report(y_test, preds, zero_division=0)

    artifact = {
        "model": model,
        "feature_names": FEATURE_NAMES,
        "version": "logreg-calibrated-v1",
        "classes": list(model.classes_),
        "train_rows": len(x_train),
        "macro_f1": float(macro_f1),
    }
    path = Path(out_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(artifact, path)

    print(f"model: {artifact['version']}  rows(train/test): {len(x_train)}/{len(x_test)}")
    print(f"macro-F1 on holdout: {macro_f1:.3f}")
    print(report)
    print(f"artifact written: {path}")
    return artifact


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", default="ml/severity_model.joblib")
    args = parser.parse_args()
    train(args.out)
