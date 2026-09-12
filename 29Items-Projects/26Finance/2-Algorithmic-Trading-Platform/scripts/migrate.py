#!/usr/bin/env python
"""Apply db/migrations/*.sql to PostgreSQL in order (idempotent).

Tracks applied versions in a ``schema_migrations`` table so re-running is safe.
Used by the `migrate` service in docker-compose and in CI before deploy.

    DATABASE_URL=postgresql://trader:pass@host:5432/trading python scripts/migrate.py
"""

from __future__ import annotations

import asyncio
import os
import sys
from pathlib import Path

MIGRATIONS_DIR = Path(__file__).resolve().parents[1] / "db" / "migrations"


async def main() -> int:
    import asyncpg

    dsn = os.environ.get("DATABASE_URL")
    if not dsn:
        print("DATABASE_URL is not set", file=sys.stderr)
        return 2
    # asyncpg wants a plain postgresql:// DSN (strip any +asyncpg driver suffix).
    dsn = dsn.replace("postgresql+asyncpg", "postgresql")

    conn = await asyncpg.connect(dsn)
    try:
        await conn.execute(
            "CREATE TABLE IF NOT EXISTS schema_migrations ("
            "  version TEXT PRIMARY KEY,"
            "  applied_at TIMESTAMPTZ NOT NULL DEFAULT now())"
        )
        applied = {r["version"] for r in await conn.fetch("SELECT version FROM schema_migrations")}

        for path in sorted(MIGRATIONS_DIR.glob("V*.sql")):
            version = path.name
            if version in applied:
                print(f"skip    {version}")
                continue
            sql = path.read_text(encoding="utf-8")
            await conn.execute(sql)  # simple protocol: supports multi-statement files
            await conn.execute("INSERT INTO schema_migrations(version) VALUES($1)", version)
            print(f"applied {version}")
    finally:
        await conn.close()
    print("migrations up to date")
    return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
