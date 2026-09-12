"""Event enrichment: normalization, redaction, template mining, idempotency keys.

The doc_id column here is the effectively-once cornerstone (TECH-NOTES pitfall #6):
reprocessed batches overwrite the same OpenSearch documents instead of duplicating them.
Must stay consistent with LogEvent.doc_id() in common/models.py.

Everything is native column expressions (no Python UDFs): the regex tables come from
common/parsing.py, which keeps Python-side tooling and the Spark pipeline in lockstep.
"""

from __future__ import annotations

from pyspark.sql import Column, DataFrame
from pyspark.sql import functions as F

from log_analytics.common.parsing import (
    _LEVEL_ALIASES,  # single source of truth
    REDACTION_PATTERNS,
    TEMPLATE_PATTERNS,
)

_ERROR_LEVELS = ("ERROR", "FATAL")


def _level_normalization_expr() -> Column:
    """when/otherwise chain from the shared alias table (no Python UDF, no serialization tax)."""
    upper = F.upper(F.trim(F.coalesce(F.col("level"), F.lit("INFO"))))
    expr: Column | None = None
    for alias, canonical in _LEVEL_ALIASES.items():
        cond = upper == alias
        expr = F.when(cond, canonical) if expr is None else expr.when(cond, canonical)
    assert expr is not None
    return expr.otherwise(F.lit("INFO"))


def _chain_regexp_replace(column: Column, patterns: tuple[tuple[str, str], ...]) -> Column:
    for pattern, replacement in patterns:
        column = F.regexp_replace(column, pattern, replacement)
    return column


def _redacted_message() -> Column:
    """Scrub PII/secrets (emails, bearer tokens, password=… pairs) before anything persists."""
    return _chain_regexp_replace(F.coalesce(F.col("message"), F.lit("")), REDACTION_PATTERNS)


def _template_id_expr() -> Column:
    """Structural template hash of the (already redacted) message — same recipe as
    common.parsing.template_id: mask ids/numbers/strings, collapse whitespace, sha1[:16]."""
    templated = _chain_regexp_replace(F.col("message"), TEMPLATE_PATTERNS)
    templated = F.trim(F.regexp_replace(templated, r"\s+", " "))
    return F.substring(F.sha1(F.substring(templated, 1, 200)), 1, 16)


def enrich(df: DataFrame) -> DataFrame:
    """Normalize levels, redact, fill defaults, stamp provenance, derive idempotent doc ids."""
    return (
        df.withColumn("level", _level_normalization_expr())
        .withColumn("message", _redacted_message())
        .withColumn("template_id", _template_id_expr())
        .withColumn("env", F.coalesce(F.col("env"), F.lit("dev")))
        .withColumn("ingest_time", F.current_timestamp())
        .withColumn("is_error", F.col("level").isin(*_ERROR_LEVELS))
        .withColumn(
            "doc_id",
            F.sha1(
                F.concat_ws(
                    "|",
                    F.col("service"),
                    F.date_format(F.col("timestamp"), "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
                    F.coalesce(F.col("host"), F.lit("")),
                    F.col("message"),
                )
            ),
        )
    )
