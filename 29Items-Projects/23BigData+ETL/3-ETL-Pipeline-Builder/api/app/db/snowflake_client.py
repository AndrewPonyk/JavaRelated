"""Snowflake access for warehouse-backed features (history, pipeline registry).

Sync connector wrapped by callers with asyncio.to_thread. The connector import
is lazy so the API starts (and unit tests run) without snowflake-connector
installed or configured. Supports password and key-pair auth.
"""

from __future__ import annotations

from collections.abc import Sequence

from app.config import get_settings


def _connect_kwargs() -> dict:
    settings = get_settings()
    kwargs: dict = {
        "account": settings.snowflake_account,
        "user": settings.snowflake_user,
        "role": settings.snowflake_role,
        "warehouse": settings.snowflake_warehouse,
        "database": settings.snowflake_database,
        "session_parameters": {"QUERY_TAG": "metrics-api"},
    }
    if settings.snowflake_private_key_path:
        # Key-pair auth (production): PEM private key mounted via secrets.
        from cryptography.hazmat.primitives import serialization

        with open(settings.snowflake_private_key_path, "rb") as fh:
            private_key = serialization.load_pem_private_key(fh.read(), password=None)
        kwargs["private_key"] = private_key.private_bytes(
            encoding=serialization.Encoding.DER,
            format=serialization.PrivateFormat.PKCS8,
            encryption_algorithm=serialization.NoEncryption(),
        )
    else:
        kwargs["password"] = settings.snowflake_password
    return kwargs


def get_connection():
    """Open a connection. Caller owns closing it."""
    import snowflake.connector  # lazy: heavy import, optional in dev/tests

    return snowflake.connector.connect(**_connect_kwargs())


def fetch_all(sql: str, params: Sequence = ()) -> list[tuple]:
    connection = get_connection()
    try:
        with connection.cursor() as cursor:
            cursor.execute(sql, params)
            return cursor.fetchall()
    finally:
        connection.close()


def execute(sql: str, params: Sequence = ()) -> int:
    """Run one DML statement; returns the affected row count."""
    connection = get_connection()
    try:
        with connection.cursor() as cursor:
            cursor.execute(sql, params)
            rowcount = cursor.rowcount or 0
        connection.commit()
        return rowcount
    finally:
        connection.close()
