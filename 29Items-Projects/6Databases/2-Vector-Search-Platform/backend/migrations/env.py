"""Alembic environment.

Pulls the DB URL from application Settings (12-factor) and targets the app's metadata so
``alembic revision --autogenerate`` can diff models. Runs migrations synchronously by
coercing the async DSN to a sync driver (psycopg) — migrations don't need async.
"""

from __future__ import annotations

from logging.config import fileConfig

# Ensure models are imported so their tables register on Base.metadata.
import app.models  # noqa: F401
from alembic import context
from app.core.config import get_settings
from app.db.base import Base
from sqlalchemy import engine_from_config, pool

config = context.config
if config.config_file_name is not None:
    fileConfig(config.config_file_name)

# Alembic needs a sync driver; map asyncpg -> psycopg for the migration run.
_sync_url = get_settings().database_url.replace("+asyncpg", "+psycopg")
config.set_main_option("sqlalchemy.url", _sync_url)

target_metadata = Base.metadata


def run_migrations_offline() -> None:
    context.configure(
        url=config.get_main_option("sqlalchemy.url"),
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
    )
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    connectable = engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )
    with connectable.connect() as connection:
        context.configure(connection=connection, target_metadata=target_metadata)
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
