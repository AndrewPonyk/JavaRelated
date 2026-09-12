"""Persistence operations for customers, predictions, and model runs.

Uses parameterized SQL only (never string-formatted) to prevent SQL injection.
All write paths run inside a transactional scope (commit on success, rollback on error).
"""

from __future__ import annotations

import json
import logging
from dataclasses import asdict, dataclass, field
from datetime import UTC, datetime
from typing import Any, cast

from sqlalchemy import CursorResult, text

from churn_predictor.db.connection import session_scope

logger = logging.getLogger(__name__)


# ===========================================================================
# Customers — full CRUD
# ===========================================================================
@dataclass
class Customer:
    """A customer feature row."""

    customer_id: str
    tenure_months: int
    monthly_charges: float
    total_charges: float | None = None
    contract_type: str = "Month-to-month"
    payment_method: str = "Electronic check"
    num_support_tickets: int = 0
    is_churned: bool | None = None


_CUSTOMER_COLUMNS = (
    "customer_id, tenure_months, monthly_charges, total_charges, "
    "contract_type, payment_method, num_support_tickets, is_churned"
)


def create_customer(customer: Customer) -> str:
    """Insert a customer; returns its id. Raises on duplicate id."""
    sql = text(f"""
        INSERT INTO customers ({_CUSTOMER_COLUMNS})
        VALUES (:customer_id, :tenure_months, :monthly_charges, :total_charges,
                :contract_type, :payment_method, :num_support_tickets, :is_churned)
        RETURNING customer_id
        """)
    with session_scope() as s:
        result = s.execute(sql, asdict(customer))
        new_id = str(result.scalar_one())
    logger.info("Created customer %s", new_id)
    return new_id


def get_customer(customer_id: str) -> Customer | None:
    """Fetch a single customer by id, or ``None`` if not found."""
    sql = text(f"SELECT {_CUSTOMER_COLUMNS} FROM customers WHERE customer_id = :cid")
    with session_scope() as s:
        row = s.execute(sql, {"cid": customer_id}).mappings().first()
    return Customer(**row) if row else None


def list_customers(*, limit: int = 100, offset: int = 0) -> list[Customer]:
    """List customers with pagination, newest first."""
    sql = text(f"""
        SELECT {_CUSTOMER_COLUMNS} FROM customers
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
        """)
    with session_scope() as s:
        rows = s.execute(sql, {"limit": limit, "offset": offset}).mappings().all()
    return [Customer(**r) for r in rows]


def count_customers() -> int:
    """Return the total number of customers."""
    with session_scope() as s:
        return int(s.execute(text("SELECT COUNT(*) FROM customers")).scalar_one())


def update_customer(customer: Customer) -> bool:
    """Update a customer in place; returns True if a row was updated."""
    sql = text("""
        UPDATE customers SET
            tenure_months = :tenure_months,
            monthly_charges = :monthly_charges,
            total_charges = :total_charges,
            contract_type = :contract_type,
            payment_method = :payment_method,
            num_support_tickets = :num_support_tickets,
            is_churned = :is_churned,
            updated_at = now()
        WHERE customer_id = :customer_id
        """)
    with session_scope() as s:
        result = cast(CursorResult, s.execute(sql, asdict(customer)))
        updated = result.rowcount > 0
    logger.info("Updated customer %s (matched=%s)", customer.customer_id, updated)
    return updated


def delete_customer(customer_id: str) -> bool:
    """Delete a customer by id; returns True if a row was deleted."""
    sql = text("DELETE FROM customers WHERE customer_id = :cid")
    with session_scope() as s:
        result = cast(CursorResult, s.execute(sql, {"cid": customer_id}))
        deleted = result.rowcount > 0
    logger.info("Deleted customer %s (matched=%s)", customer_id, deleted)
    return deleted


def upsert_customers_bulk(customers: list[Customer]) -> int:
    """Insert-or-update many customers (used for seeding / bulk import)."""
    if not customers:
        return 0
    sql = text(f"""
        INSERT INTO customers ({_CUSTOMER_COLUMNS})
        VALUES (:customer_id, :tenure_months, :monthly_charges, :total_charges,
                :contract_type, :payment_method, :num_support_tickets, :is_churned)
        ON CONFLICT (customer_id) DO UPDATE SET
            tenure_months = EXCLUDED.tenure_months,
            monthly_charges = EXCLUDED.monthly_charges,
            total_charges = EXCLUDED.total_charges,
            contract_type = EXCLUDED.contract_type,
            payment_method = EXCLUDED.payment_method,
            num_support_tickets = EXCLUDED.num_support_tickets,
            is_churned = EXCLUDED.is_churned,
            updated_at = now()
        """)
    with session_scope() as s:
        s.execute(sql, [asdict(c) for c in customers])
    logger.info("Upserted %d customers", len(customers))
    return len(customers)


# ===========================================================================
# Predictions — audit trail
# ===========================================================================
@dataclass
class PredictionRecord:
    """A scored prediction to persist for audit + monitoring."""

    customer_id: str
    churn_probability: float
    risk_band: str
    model_algorithm: str
    top_drivers: list[dict[str, Any]] = field(default_factory=list)
    actor: str = "system"


def save_prediction(record: PredictionRecord) -> int:
    """Insert a single prediction row; returns the new row id."""
    sql = text("""
        INSERT INTO predictions
            (customer_id, churn_probability, risk_band, model_algorithm,
             top_drivers, actor, created_at)
        VALUES
            (:customer_id, :proba, :band, :algo, :drivers, :actor, :ts)
        RETURNING id
        """)
    with session_scope() as s:
        result = s.execute(
            sql,
            {
                "customer_id": record.customer_id,
                "proba": record.churn_probability,
                "band": record.risk_band,
                "algo": record.model_algorithm,
                "drivers": json.dumps(record.top_drivers),
                "actor": record.actor,
                "ts": datetime.now(UTC),
            },
        )
        new_id = int(result.scalar_one())
    logger.info("Saved prediction id=%d customer=%s", new_id, record.customer_id)
    return new_id


def save_predictions_bulk(records: list[PredictionRecord]) -> int:
    """Bulk-insert predictions (batch scoring). Returns count inserted."""
    if not records:
        return 0
    sql = text("""
        INSERT INTO predictions
            (customer_id, churn_probability, risk_band, model_algorithm,
             top_drivers, actor, created_at)
        VALUES
            (:customer_id, :proba, :band, :algo, :drivers, :actor, :ts)
        """)
    now = datetime.now(UTC)
    params = [
        {
            "customer_id": r.customer_id,
            "proba": r.churn_probability,
            "band": r.risk_band,
            "algo": r.model_algorithm,
            "drivers": json.dumps(r.top_drivers),
            "actor": r.actor,
            "ts": now,
        }
        for r in records
    ]
    with session_scope() as s:
        s.execute(sql, params)
    logger.info("Bulk-saved %d predictions", len(records))
    return len(records)


def recent_predictions(*, limit: int = 50) -> list[dict[str, Any]]:
    """Return the most recent predictions (for the audit/insights view)."""
    sql = text("""
        SELECT id, customer_id, churn_probability, risk_band, model_algorithm,
               actor, created_at
        FROM predictions
        ORDER BY created_at DESC
        LIMIT :limit
        """)
    with session_scope() as s:
        rows = s.execute(sql, {"limit": limit}).mappings().all()
    return [dict(r) for r in rows]


# ===========================================================================
# Model runs — lineage / registry
# ===========================================================================
def save_model_run(metadata: dict[str, Any]) -> int:
    """Record a training run for model registry / lineage."""
    sql = text("""
        INSERT INTO model_runs
            (algorithm, metrics, params, feature_columns, trained_at, created_at)
        VALUES
            (:algo, :metrics, :params, :features, :trained_at, :ts)
        RETURNING id
        """)
    with session_scope() as s:
        result = s.execute(
            sql,
            {
                "algo": metadata["algorithm"],
                "metrics": json.dumps(metadata["metrics"]),
                "params": json.dumps(metadata["params"]),
                "features": json.dumps(metadata["feature_columns"]),
                "trained_at": metadata["trained_at"],
                "ts": datetime.now(UTC),
            },
        )
        return int(result.scalar_one())


def latest_model_run() -> dict[str, Any] | None:
    """Return the most recent model run metadata, or ``None``."""
    sql = text("""
        SELECT id, algorithm, metrics, params, trained_at, created_at
        FROM model_runs ORDER BY created_at DESC LIMIT 1
        """)
    with session_scope() as s:
        row = s.execute(sql).mappings().first()
    return dict(row) if row else None
