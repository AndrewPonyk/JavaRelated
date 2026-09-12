"""Performance indexes for the hot query paths.

- products: the catalog list endpoint filters is_active and orders by created_at.
- search_events: LTR tooling and per-product click lookups filter on
  clicked_product_id, which is NULL for the (majority) impression rows —
  a partial index keeps it small.

Revision ID: 0003
Revises: 0002
Create Date: 2026-07-02
"""

import sqlalchemy as sa
from alembic import op

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_index(
        "ix_products_active_created",
        "products",
        ["is_active", "created_at"],
    )
    op.create_index(
        "ix_search_events_clicked_product",
        "search_events",
        ["clicked_product_id"],
        postgresql_where=sa.text("clicked_product_id IS NOT NULL"),
    )


def downgrade() -> None:
    op.drop_index("ix_search_events_clicked_product", table_name="search_events")
    op.drop_index("ix_products_active_created", table_name="products")
