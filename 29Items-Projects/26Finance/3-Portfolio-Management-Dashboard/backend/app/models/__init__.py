"""ORM models. Importing them here registers every table on ``Base.metadata``
so Alembic autogenerate and ``create_all`` see the full schema.
"""

from app.db.base import Base
from app.models.asset import Asset, PriceBar
from app.models.holding import Holding
from app.models.job import SimulationJob
from app.models.portfolio import Portfolio
from app.models.user import User

__all__ = [
    "Base",
    "User",
    "Asset",
    "PriceBar",
    "Portfolio",
    "Holding",
    "SimulationJob",
]
