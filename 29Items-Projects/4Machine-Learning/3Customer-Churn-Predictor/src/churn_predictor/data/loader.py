"""Load customer data from PostgreSQL or a CSV fallback.

A single entry point (:func:`load_customers`) abstracts the source so callers
(training pipeline, batch scoring) don't care whether data lives in the DB or a file.
"""

from __future__ import annotations

import logging
from datetime import datetime
from pathlib import Path

import pandas as pd
from sqlalchemy import text

from churn_predictor import DataLoadError
from churn_predictor.db.connection import get_engine

logger = logging.getLogger(__name__)

# Columns the rest of the pipeline expects to exist. Keep in sync with migrations.
EXPECTED_COLUMNS: tuple[str, ...] = (
    "customer_id",
    "tenure_months",
    "monthly_charges",
    "total_charges",
    "contract_type",
    "payment_method",
    "num_support_tickets",
    "is_churned",  # target (nullable for scoring data)
)


def load_from_postgres(
    query: str | None = None,
    *,
    since: datetime | None = None,
    limit: int | None = None,
) -> pd.DataFrame:
    """Load customer rows from PostgreSQL into a DataFrame.

    Parameters
    ----------
    query:
        Optional full SQL override. When given, ``since``/``limit`` are ignored.
    since:
        Incremental load — only rows with ``updated_at`` newer than this.
    limit:
        Cap the number of rows returned.
    """
    params: dict[str, object] = {}
    if query is None:
        sql_text = "SELECT * FROM customers"
        if since is not None:
            sql_text += " WHERE updated_at > :since"
            params["since"] = since
        sql_text += " ORDER BY created_at"
        if limit is not None:
            sql_text += " LIMIT :limit"
            params["limit"] = limit
    else:
        sql_text = query

    try:
        engine = get_engine()
        with engine.connect() as conn:
            df = pd.read_sql(text(sql_text), conn, params=params or None)
    except Exception as exc:  # noqa: BLE001 - wrap as domain error
        raise DataLoadError(f"Failed to read from PostgreSQL: {exc}") from exc

    logger.info("Loaded %d rows from PostgreSQL", len(df))
    return df


def load_from_csv(path: str | Path) -> pd.DataFrame:
    """Load customer rows from a CSV file."""
    p = Path(path)
    if not p.exists():
        raise DataLoadError(f"CSV not found: {p}")
    df = pd.read_csv(p)
    logger.info("Loaded %d rows from CSV %s", len(df), p)
    return df


def validate_schema(df: pd.DataFrame, *, require_target: bool = True) -> None:
    """Ensure required columns are present; raise :class:`DataLoadError` otherwise."""
    required = set(EXPECTED_COLUMNS)
    if not require_target:
        required.discard("is_churned")
    missing = required - set(df.columns)
    if missing:
        raise DataLoadError(f"Missing expected columns: {sorted(missing)}")


def load_customers(
    source: str = "postgres",
    *,
    path: str | Path | None = None,
    require_target: bool = True,
    since: datetime | None = None,
    limit: int | None = None,
) -> pd.DataFrame:
    """Unified loader. ``source`` is ``"postgres"`` or ``"csv"``.

    Supports incremental (``since``) and capped (``limit``) loads from PostgreSQL.
    """
    if source == "postgres":
        df = load_from_postgres(since=since, limit=limit)
    elif source == "csv":
        if path is None:
            raise DataLoadError("path is required when source='csv'")
        df = load_from_csv(path)
        if limit is not None:
            df = df.head(limit)
    else:
        raise DataLoadError(f"Unknown source: {source!r}")

    validate_schema(df, require_target=require_target)
    return df
