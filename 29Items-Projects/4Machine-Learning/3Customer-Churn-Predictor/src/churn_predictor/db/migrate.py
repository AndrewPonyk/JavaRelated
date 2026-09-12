"""Cross-platform SQL migration runner.

Applies the forward-only ``migrations/*.sql`` files in lexical order using the
configured SQLAlchemy engine, so migrations can run on Windows dev machines and in
CI without a local ``psql`` binary. Migrations are idempotent (``IF NOT EXISTS``).
"""

from __future__ import annotations

import logging
from pathlib import Path

from sqlalchemy import Engine

from churn_predictor.db.connection import get_engine

logger = logging.getLogger(__name__)

# repo_root/src/churn_predictor/db/migrate.py -> repo_root/migrations
MIGRATIONS_DIR = Path(__file__).resolve().parents[3] / "migrations"

_SKIP_STATEMENTS = {"begin", "commit"}


def _split_statements(sql: str) -> list[str]:
    """Split a SQL script into executable statements, dropping comments/transaction wrappers."""
    statements: list[str] = []
    for chunk in sql.split(";"):
        lines = [
            line
            for line in chunk.splitlines()
            if line.strip() and not line.strip().startswith("--")
        ]
        statement = "\n".join(lines).strip()
        if statement and statement.lower() not in _SKIP_STATEMENTS:
            statements.append(statement)
    return statements


def apply_migrations(
    engine: Engine | None = None, *, migrations_dir: Path | None = None
) -> list[str]:
    """Apply all migrations in order. Returns the list of files applied."""
    engine = engine or get_engine()
    directory = migrations_dir or MIGRATIONS_DIR
    files = sorted(directory.glob("*.sql"))
    if not files:
        logger.warning("No migration files found in %s", directory)
        return []

    applied: list[str] = []
    for path in files:
        statements = _split_statements(path.read_text(encoding="utf-8"))
        with engine.begin() as conn:
            for statement in statements:
                conn.exec_driver_sql(statement)
        logger.info("Applied migration %s (%d statements)", path.name, len(statements))
        applied.append(path.name)
    return applied


if __name__ == "__main__":  # pragma: no cover - manual entrypoint
    from churn_predictor.config import configure_logging

    configure_logging()
    apply_migrations()
