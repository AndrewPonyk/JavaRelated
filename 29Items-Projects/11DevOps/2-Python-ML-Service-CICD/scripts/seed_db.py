"""Seed the database with realistic demo predictions.

Inserts randomized (seeded RNG) prediction rows including the served
feature vectors, so drift checks and dashboards have data to work with.

Usage:
    python scripts/seed_db.py --count 200
    python scripts/seed_db.py --count 50 --database-url sqlite:///./fraud.db

The database URL is resolved (in order) from --database-url, the
FRAUD_DATABASE_URL environment variable / .env file, then the local
SQLite default. Tables are created when missing, so the script works
out of the box on a fresh checkout.
"""

from __future__ import annotations

import argparse
import random
import sys
import uuid
from datetime import UTC, datetime, timedelta
from decimal import Decimal
from pathlib import Path

# Make the application package importable (src layout).
PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from sqlalchemy import create_engine  # noqa: E402
from sqlalchemy.exc import SQLAlchemyError  # noqa: E402
from sqlalchemy.orm import sessionmaker  # noqa: E402

from fraud_detection.core.config import get_settings  # noqa: E402
from fraud_detection.db.models import Base, Prediction  # noqa: E402
from fraud_detection.ml.features import build_feature_vector  # noqa: E402

MERCHANT_CATEGORIES = [
    "grocery",
    "electronics",
    "travel",
    "gambling",
    "jewelry",
    "restaurants",
    "fuel",
    "entertainment",
]


def seed(database_url: str, count: int, seed_value: int) -> int:
    """Insert ``count`` randomized prediction rows.

    Args:
        database_url: Target SQLAlchemy URL.
        count: Number of rows to insert.
        seed_value: RNG seed (also namespaces the transaction ids so the
            script can be re-run with different seeds without collisions).

    Returns:
        Number of rows written.
    """
    rng = random.Random(seed_value)
    engine = create_engine(
        database_url,
        connect_args={"check_same_thread": False} if database_url.startswith("sqlite") else {},
    )
    Base.metadata.create_all(bind=engine)
    factory = sessionmaker(bind=engine, expire_on_commit=False)

    written = 0
    now = datetime.now(UTC)
    with factory() as session:
        for i in range(count):
            amount = round(rng.lognormvariate(4.0, 1.2), 2)
            category = rng.choice(MERCHANT_CATEGORIES)
            timestamp = now - timedelta(minutes=rng.randint(0, 60 * 24 * 7))
            features = build_feature_vector(amount, category, timestamp, {})
            probability = min(max(rng.gauss(0.18, 0.2), 0.0), 1.0)
            session.add(
                Prediction(
                    id=uuid.uuid4(),
                    transaction_id=f"seed-{seed_value}-{i}",
                    account_id=f"acct-{rng.randint(1, max(count // 4, 1))}",
                    amount=Decimal(str(amount)),
                    model_version="seed",
                    variant="champion" if rng.random() >= 0.1 else "challenger",
                    fraud_probability=probability,
                    is_fraud=probability >= 0.5,
                    latency_ms=rng.uniform(0.5, 8.0),
                    features=features,
                    created_at=timestamp,
                )
            )
            written += 1
        session.commit()
    engine.dispose()
    return written


def main(argv: list[str] | None = None) -> int:
    """CLI entry point; returns a process exit code."""
    parser = argparse.ArgumentParser(description="Seed demo prediction rows")
    parser.add_argument("--count", type=int, default=100, help="Number of rows to insert")
    parser.add_argument("--seed", type=int, default=42, help="RNG seed")
    parser.add_argument(
        "--database-url",
        default=None,
        help="SQLAlchemy URL (defaults to FRAUD_DATABASE_URL / app settings)",
    )
    args = parser.parse_args(argv)

    database_url = args.database_url or get_settings().database_url
    try:
        written = seed(database_url, args.count, args.seed)
    except SQLAlchemyError as exc:
        print(f"Could not seed the database: {exc}", file=sys.stderr)
        print(
            "Hints:\n"
            "  - Is the database reachable? (for Postgres: docker compose up -d db)\n"
            f"  - Is the URL correct? (using: {database_url})"
        )
        return 1
    print(f"Seeded {written} predictions into {database_url}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
