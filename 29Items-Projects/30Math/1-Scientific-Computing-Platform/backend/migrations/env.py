"""Alembic environment. Reads DATABASE_URL from the process environment so
migrations run identically on a laptop, in docker-compose, and as the one-off
ECS task in deploy.yml (docs/TECH-NOTES.md §3.1).
"""

from __future__ import annotations

import os
from logging.config import fileConfig

from alembic import context
from sqlalchemy import engine_from_config, pool

from app.db.models import Base

config = context.config

if config.config_file_name is not None:
    fileConfig(config.config_file_name)

target_metadata = Base.metadata


def _database_url() -> str:
    # psycopg3 serves both sync (Alembic) and async (app) through the same
    # "postgresql+psycopg://" URL — no driver-name rewriting needed.
    return os.environ.get("DATABASE_URL", "postgresql+psycopg://scp:scp@localhost:5432/scp")


def run_migrations_offline() -> None:
    """Emit SQL to stdout without a DB connection (--sql mode)."""
    context.configure(
        url=_database_url(),
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
    )
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    configuration = config.get_section(config.config_ini_section, {})
    configuration["sqlalchemy.url"] = _database_url()
    connectable = engine_from_config(configuration, prefix="sqlalchemy.", poolclass=pool.NullPool)
    with connectable.connect() as connection:
        context.configure(connection=connection, target_metadata=target_metadata)
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
