"""Data-quality gate, run between every layer promotion.

Checks run in production on every pipeline run (unlike code tests): production
data changes even when code doesn't. Results are persisted to the DQ results
Delta table (audit trail + pass-rate dashboards); a `fail`-severity violation
exits non-zero, which makes Airflow block all downstream tasks for that table.

The registry is code-owned so check changes go through review like any other
contract change.

Run:
    python -m lakehouse.quality.checks --table silver/sales/orders --run-date 2026-07-01
"""

from __future__ import annotations

import argparse
import sys
from collections.abc import Callable
from dataclasses import dataclass

from pyspark.sql import Column, DataFrame
from pyspark.sql import functions as F


@dataclass(frozen=True)
class Check:
    name: str
    predicate: Callable[[], Column]  # row-level: True == violation
    severity: str = "fail"  # "fail" blocks downstream; "warn" logs + metric


@dataclass(frozen=True)
class CheckResult:
    name: str
    severity: str
    violations: int

    @property
    def passed(self) -> bool:
        return self.violations == 0


def run_checks(df: DataFrame, checks: list[Check]) -> list[CheckResult]:
    """Evaluate all checks in a single pass over the data."""
    aggs = [F.sum(F.when(check.predicate(), 1).otherwise(0)).alias(check.name) for check in checks]
    row = df.agg(*aggs).collect()[0]
    return [CheckResult(check.name, check.severity, int(row[check.name] or 0)) for check in checks]


# --- Table check registry ----------------------------------------------------

SILVER_ORDERS_CHECKS = [
    Check("order_id_not_null", lambda: F.col("order_id").isNull()),
    Check("amount_non_negative", lambda: F.col("amount") < 0),
    Check("event_date_not_in_future", lambda: F.col("event_date") > F.current_date()),
    # NULL-safe: `~isin` alone is NULL (never true) for NULL currency values.
    Check(
        "known_currency",
        lambda: F.col("currency").isNull() | ~F.col("currency").isin("EUR", "USD"),
        severity="warn",
    ),
]

GOLD_DAILY_STATS_CHECKS = [
    Check("order_count_positive", lambda: F.col("order_count") <= 0),
    Check("gross_amount_non_negative", lambda: F.col("gross_amount") < 0),
]

REGISTRY: dict[str, list[Check]] = {
    "silver/sales/orders": SILVER_ORDERS_CHECKS,
    "gold/sales/order_daily_stats": GOLD_DAILY_STATS_CHECKS,
}


def main() -> None:
    from lakehouse.common.cli import run_date_arg
    from lakehouse.common.config import LakehouseSettings
    from lakehouse.common.logging import get_logger
    from lakehouse.common.metrics import record_dq_results
    from lakehouse.common.spark import build_spark_session

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--table", required=True, help="layer/domain/table, e.g. silver/sales/orders"
    )
    parser.add_argument("--run-date", required=True, type=run_date_arg)
    args = parser.parse_args()

    if args.table not in REGISTRY:
        raise SystemExit(f"no checks registered for {args.table!r} — add them to the registry")

    settings = LakehouseSettings.from_env()
    log = get_logger("quality_gate", table=args.table, run_date=args.run_date)
    spark = build_spark_session("quality_gate", settings)

    layer, domain, table = args.table.split("/")
    df = spark.read.format("delta").load(settings.layer_path(layer, domain, table))
    # Scope to the partition being promoted; full-table checks run weekly instead.
    if "event_date" in df.columns:
        df = df.filter(F.col("event_date") == F.lit(args.run_date))

    results = run_checks(df, REGISTRY[args.table])
    record_dq_results(spark, settings, table=args.table, run_date=args.run_date, results=results)

    failed = [r for r in results if not r.passed and r.severity == "fail"]
    for result in results:
        level = log.error if result in failed else log.info
        level(
            "dq check",
            extra={"context": {"check": result.name, "violations": result.violations}},
        )

    if failed:
        sys.exit(1)  # non-zero exit is the contract with Airflow


if __name__ == "__main__":
    main()
