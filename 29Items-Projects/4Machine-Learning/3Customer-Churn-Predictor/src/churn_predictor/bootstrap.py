"""One-shot bootstrap: wait for DB → migrate → seed → train.

Run as ``python -m churn_predictor.bootstrap``. This is the command the Docker
``trainer`` service runs so that ``docker compose up`` yields a fully working app
(schema applied, demo data seeded, a model trained) with no manual steps.
"""

from __future__ import annotations

import logging
import time

from sqlalchemy import text

from churn_predictor.config import configure_logging, settings
from churn_predictor.data.loader import load_customers
from churn_predictor.data.synthetic import generate_synthetic_customers
from churn_predictor.db.connection import get_engine
from churn_predictor.db.migrate import apply_migrations
from churn_predictor.db.repository import Customer, count_customers, upsert_customers_bulk
from churn_predictor.models.train import train_model

logger = logging.getLogger(__name__)


def wait_for_db(*, retries: int = 30, delay: float = 2.0) -> None:
    """Block until PostgreSQL accepts a connection (or give up)."""
    engine = get_engine()
    for attempt in range(1, retries + 1):
        try:
            with engine.connect() as conn:
                conn.execute(text("SELECT 1"))
            logger.info("Database is ready")
            return
        except Exception as exc:  # noqa: BLE001
            logger.info("Waiting for DB (%d/%d): %s", attempt, retries, exc)
            time.sleep(delay)
    raise RuntimeError("Database did not become ready in time")


def seed_if_empty(n: int = 2000) -> int:
    """Seed synthetic customers when the table is empty. Returns rows inserted."""
    if count_customers() > 0:
        logger.info("Customers already present; skipping seed")
        return 0
    df = generate_synthetic_customers(n)
    customers = [
        Customer(
            customer_id=row.customer_id,
            tenure_months=int(row.tenure_months),
            monthly_charges=float(row.monthly_charges),
            total_charges=float(row.total_charges),
            contract_type=row.contract_type,
            payment_method=row.payment_method,
            num_support_tickets=int(row.num_support_tickets),
            is_churned=bool(row.is_churned),
        )
        for row in df.itertuples(index=False)
    ]
    inserted = upsert_customers_bulk(customers)
    logger.info("Seeded %d synthetic customers", inserted)
    return inserted


def main() -> None:
    configure_logging()
    logger.info("Bootstrap starting (env=%s)", settings.app_env)

    wait_for_db()
    apply_migrations()
    seed_if_empty()

    df = load_customers(source="postgres")
    metadata = train_model(df, log_run=True)
    logger.info(
        "Bootstrap complete: %s model, holdout AUC=%.4f",
        metadata.algorithm,
        metadata.metrics["holdout_roc_auc"],
    )


if __name__ == "__main__":
    main()
