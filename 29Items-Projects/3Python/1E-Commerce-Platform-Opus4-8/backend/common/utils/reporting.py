"""SQLAlchemy engine for complex, read-only reporting queries.

Per ARCHITECTURE.md §2.4, heavy analytical reads bypass the Django ORM and run
against a read replica via SQLAlchemy Core. Writes always go through the ORM.
"""

from __future__ import annotations

from functools import lru_cache

from django.conf import settings
from sqlalchemy import Engine, create_engine, text


@lru_cache(maxsize=1)
def get_read_engine() -> Engine:
    """Return a process-wide SQLAlchemy engine bound to the read replica."""
    url = settings.READ_REPLICA_URL
    # SQLAlchemy expects the postgresql+psycopg dialect; normalise the Django URL.
    if url.startswith("postgres://"):
        url = url.replace("postgres://", "postgresql+psycopg://", 1)
    elif url.startswith("postgresql://"):
        url = url.replace("postgresql://", "postgresql+psycopg://", 1)
    # NOTE: pool sizing must be coordinated with the Django ORM pool and
    # pgbouncer limits (see TECH-NOTES §3.6).
    return create_engine(url, pool_size=5, max_overflow=10, pool_pre_ping=True)


def top_selling_products(limit: int = 10) -> list[dict]:
    """Return the best-selling products by total units sold (paid orders only)."""
    sql = text("""
        SELECT oi.product_id        AS product_id,
               SUM(oi.quantity)     AS units_sold,
               SUM(oi.quantity * oi.unit_price) AS revenue
        FROM orders_orderitem oi
        JOIN orders_order o ON o.id = oi.order_id
        WHERE o.status IN ('paid', 'fulfilled')
        GROUP BY oi.product_id
        ORDER BY units_sold DESC
        LIMIT :limit
        """)
    with get_read_engine().connect() as conn:
        rows = conn.execute(sql, {"limit": limit})
        return [dict(row._mapping) for row in rows]
