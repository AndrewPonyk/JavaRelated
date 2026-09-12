"""Add the features JSON column to predictions (drift monitoring input).

Revision ID: 0002
Revises: 0001
Create Date: 2026-07-12

"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

# revision identifiers, used by Alembic.
revision: str = "0002"
down_revision: Union[str, None] = "0001"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Store the served feature vector with every prediction."""
    op.add_column("predictions", sa.Column("features", sa.JSON(), nullable=True))


def downgrade() -> None:
    """Drop the features column."""
    op.drop_column("predictions", "features")
