"""Schema parity — migrations/001_init.sql must match what the ORM declares.

Production boot order is: SQL migrations run first (deploy pre-step), then
db.create_all() skips tables that already exist. So any drift between this
file and the models becomes an OperationalError on the first affected query
in production — while dev (create_all only) stays green. This test builds
both schemas independently and compares columns and indexes, closing that
gap permanently.
"""

import glob
import os

import sqlalchemy

from crypto_toolkit.extensions import db

MIGRATIONS_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "migrations")


def _migration_scripts() -> list:
    files = sorted(glob.glob(os.path.join(MIGRATIONS_DIR, "*.sql")))
    assert files, "no migration files found — path drifted?"
    return [open(path, encoding="utf-8").read() for path in files]


def _engine_with_migrations() -> sqlalchemy.Engine:
    """In-memory engine built by applying the SQL migrations, prod-style."""
    engine = sqlalchemy.create_engine("sqlite://")
    raw = engine.raw_connection()
    for script in _migration_scripts():
        raw.executescript(script)
    raw.close()
    return engine


def _engine_with_orm() -> sqlalchemy.Engine:
    """In-memory engine built by create_all(), dev-style."""
    engine = sqlalchemy.create_engine("sqlite://")
    db.metadata.create_all(engine)
    return engine


def _snapshot(engine: sqlalchemy.Engine) -> dict:
    tables = {}
    with engine.connect() as conn:
        names = conn.exec_driver_sql(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
        )
        for (name,) in names:
            # (name, declared type, notnull, pk) — defaults are client-side in the ORM
            columns = [
                (row[1], row[2].upper(), row[3], row[5])
                for row in conn.exec_driver_sql(f"PRAGMA table_info({name})")
            ]
            # sqlite_autoindex* back UNIQUE constraints; the rest are deliberate
            indexes = sorted(
                row[1]
                for row in conn.exec_driver_sql(f"PRAGMA index_list({name})")
                if not row[1].startswith("sqlite_autoindex")
            )
            tables[name] = {"columns": columns, "indexes": indexes}
    return tables


def test_migrations_match_orm(app):
    by_sql = _snapshot(_engine_with_migrations())
    by_orm = _snapshot(_engine_with_orm())

    assert set(by_sql) == set(by_orm), f"table sets differ: {set(by_sql) ^ set(by_orm)}"
    for table in by_orm:
        assert by_sql[table]["columns"] == by_orm[table]["columns"], (
            f"{table}: columns differ between migration and ORM.\n"
            f"  migration: {by_sql[table]['columns']}\n  ORM:        {by_orm[table]['columns']}"
        )
        assert by_sql[table]["indexes"] == by_orm[table]["indexes"], (
            f"{table}: indexes differ.\n"
            f"  migration: {by_sql[table]['indexes']}\n  ORM:        {by_orm[table]['indexes']}"
        )


def test_orm_bootstraps_cleanly_over_applied_migrations(app):
    """The exact production sequence: migrations first, create_all second.

    create_all must be a no-op over the migrated schema (it is checkfirst) —
    and afterwards every column the ORM declares must exist, which is what
    broke when column names drifted.
    """
    engine = _engine_with_migrations()
    db.metadata.create_all(engine, checkfirst=True)

    with engine.begin() as conn:
        conn.exec_driver_sql(
            "INSERT INTO users (email, password_hash, is_admin, totp_secret_encrypted, created_at)"
            " VALUES ('parity@toolkit.test', 'x', 0, 'fernet-blob', datetime('now'))"
        )
        conn.exec_driver_sql(
            "INSERT INTO lessons (slug, title, topic, created_at, updated_at)"
            " VALUES ('parity', 'P', 'aes', datetime('now'), datetime('now'))"
        )
        conn.exec_driver_sql(
            "INSERT INTO audit_log (op, created_at) VALUES ('parity.check', datetime('now'))"
        )
        # read back through the exact attribute names the ORM would use
        got_totp = conn.exec_driver_sql("SELECT totp_secret_encrypted FROM users").fetchone()
        assert got_totp[0] == "fernet-blob"
        assert conn.exec_driver_sql("SELECT updated_at FROM lessons").fetchone()[0] is not None
        assert conn.exec_driver_sql("SELECT op FROM audit_log").fetchone()[0] == "parity.check"
    engine.dispose()
