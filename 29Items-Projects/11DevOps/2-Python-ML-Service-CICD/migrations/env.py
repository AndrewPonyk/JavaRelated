"""Alembic migration environment for the fraud-detection service.

Resolves the database URL from the ``FRAUD_DATABASE_URL`` environment variable
(falling back to the local development default) and wires the application's
SQLAlchemy metadata for ``--autogenerate`` support.
"""

from __future__ import annotations

import os
import sys
from logging.config import fileConfig
from pathlib import Path

from alembic import context
from sqlalchemy import engine_from_config, pool

# Make the application package importable (src layout).
PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

# Alembic Config object (values from alembic.ini).
config = context.config

# Configure Python logging from the ini file, when present.
if config.config_file_name is not None:
    fileConfig(config.config_file_name)

try:
    from fraud_detection.core.config import get_settings

    DEFAULT_DATABASE_URL = get_settings().database_url
except Exception:  # pragma: no cover - defensive fallback
    DEFAULT_DATABASE_URL = "sqlite:///./fraud.db"

database_url = os.environ.get("FRAUD_DATABASE_URL", DEFAULT_DATABASE_URL)
# Escape '%' so configparser interpolation does not mangle URL-encoded chars.
config.set_main_option("sqlalchemy.url", database_url.replace("%", "%%"))

# Application metadata for autogenerate. Guarded so plain `alembic upgrade`
# still works in minimal environments where the app package is unavailable.
try:
    from fraud_detection.db.models import Base

    target_metadata = Base.metadata
except ImportError:  # pragma: no cover - defensive fallback
    target_metadata = None


def run_migrations_offline() -> None:
    """Run migrations in 'offline' mode (emit SQL to stdout, no DBAPI needed)."""
    url = config.get_main_option("sqlalchemy.url")
    context.configure(
        url=url,
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        compare_type=True,
    )

    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    """Run migrations in 'online' mode (connect and apply directly)."""
    connectable = engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )

    with connectable.connect() as connection:
        context.configure(
            connection=connection,
            target_metadata=target_metadata,
            compare_type=True,
        )

        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
