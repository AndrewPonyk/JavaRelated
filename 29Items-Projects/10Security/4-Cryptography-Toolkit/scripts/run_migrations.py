#!/usr/bin/env python
"""Applies migrations/*.sql to DATABASE_URL (SQLite supported out of the box).

Usage:
    python scripts/run_migrations.py                     # uses DATABASE_URL / default sqlite file
    DATABASE_URL=sqlite:///prod.db python scripts/run_migrations.py

For PostgreSQL, run the same files with:  psql "$DATABASE_URL" -f migrations/001_init.sql
(SQL files are written portably and are idempotent via IF NOT EXISTS.)
"""

from __future__ import annotations

import glob
import os
import sqlite3
import sys

MIGRATIONS_DIR = os.path.join(os.path.dirname(__file__), "..", "migrations")


def default_db_path() -> str:
    url = os.environ.get("DATABASE_URL", "sqlite:///crypto_toolkit.db")
    if url.startswith("sqlite:///"):
        return url.removeprefix("sqlite:///")
    raise SystemExit(
        f"DATABASE_URL {url!r} is not SQLite — apply migrations/*.sql with psql instead."
    )


def main() -> int:
    db_path = default_db_path()
    files = sorted(glob.glob(os.path.join(MIGRATIONS_DIR, "*.sql")))
    if not files:
        print("No migration files found.")
        return 1
    conn = sqlite3.connect(db_path)
    try:
        for path in files:
            with open(path, encoding="utf-8") as fh:
                sql = fh.read()
            conn.executescript(sql)
            conn.commit()
            print(f"applied: {os.path.basename(path)}")
    finally:
        conn.close()
    print(f"migrations complete -> {db_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
