"""Feature extraction shared by training (ml/train.py) and inference
(app/services/ml_classifier.py) — one definition so they cannot drift.

All features are cheap, deterministic, and bounded (≤ 32 floats).
"""

from __future__ import annotations

import hashlib
import re

FEATURE_NAMES: list[str] = [
    # source one-hot (3)
    "src_zap",
    "src_sqlmap",
    "src_xss_engine",
    # rule-family hash buckets (8)
    *[f"rule_bucket_{i}" for i in range(8)],
    # text signals (5)
    "title_len",
    "desc_len",
    "has_param",
    "param_id_like",
    "has_method_post",
    # keyword flags (8)
    "kw_sql",
    "kw_xss",
    "kw_injection",
    "kw_admin",
    "kw_session",
    "kw_cookie",
    "kw_header",
    "kw_disclosure",
]

_KEYWORDS = {
    "kw_sql": ("sql", "sqli", "database", "dbms", "union select"),
    "kw_xss": ("xss", "cross-site scripting", "cross site scripting", "script injection"),
    "kw_injection": ("injection", "inject", "rce", "command execution", "deserial"),
    "kw_admin": ("admin", "privilege", "authentication bypass", "auth bypass"),
    "kw_session": ("session", "token", "jwt", "csrf"),
    "kw_cookie": ("cookie", "set-cookie", "same-site"),
    "kw_header": ("header", "csp", "cors", "content-security", "hsts"),
    "kw_disclosure": ("disclosure", "leak", "sensitive", "information exposure", "verbose"),
}

_ID_LIKE = re.compile(r"^(id|uid|uuid|[a-z_]*_id|num|page|ref)$", re.I)


def extract_features(
    *,
    source: str,
    rule_id: str,
    title: str = "",
    description: str = "",
    param: str | None = None,
    method: str | None = None,
) -> list[float]:
    text = f"{title} {description}".lower()
    vector = [
        float(source == "zap"),
        float(source == "sqlmap"),
        float(source == "xss_engine"),
    ]
    # stable rule-family buckets from the rule identifier
    digest = hashlib.sha256(rule_id.encode()).digest()
    for byte in digest[:8]:
        vector.append(float(byte) / 255.0)
    vector.extend(
        [
            float(min(len(title), 200)),
            float(min(len(description), 2000)),
            float(param is not None),
            float(bool(param and _ID_LIKE.match(param.strip()))),
            float((method or "").upper() == "POST"),
        ]
    )
    for name in (
        "kw_sql",
        "kw_xss",
        "kw_injection",
        "kw_admin",
        "kw_session",
        "kw_cookie",
        "kw_header",
        "kw_disclosure",
    ):
        vector.append(float(any(k in text for k in _KEYWORDS[name])))
    return vector
