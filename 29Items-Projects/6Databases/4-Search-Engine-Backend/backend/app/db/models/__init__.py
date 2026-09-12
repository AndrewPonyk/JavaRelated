"""Import all models so SQLAlchemy's registry and Alembic autogenerate see them."""

from app.db.models.category import Category
from app.db.models.index_outbox import IndexOutbox
from app.db.models.product import Product
from app.db.models.search_event import SearchEvent

__all__ = ["Category", "IndexOutbox", "Product", "SearchEvent"]
