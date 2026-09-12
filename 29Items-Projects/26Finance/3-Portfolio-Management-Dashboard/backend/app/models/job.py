from __future__ import annotations

import uuid

from sqlalchemy import ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy.types import JSON

from app.db.base import Base, TimestampMixin


def _new_id() -> str:
    return uuid.uuid4().hex


class SimulationJob(Base, TimestampMixin):
    """An async Monte Carlo job. Holds status + the persisted result payload.

    Matches the ARCHITECTURE data-flow: the worker persists the SimulationResult
    here, and clients poll ``GET /api/v1/jobs/{id}`` for status/result.
    """

    __tablename__ = "simulation_jobs"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_new_id)
    portfolio_id: Mapped[int] = mapped_column(
        ForeignKey("portfolios.id", ondelete="CASCADE"), index=True, nullable=False
    )
    owner_id: Mapped[int] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )
    status: Mapped[str] = mapped_column(String(16), default="queued", nullable=False)
    job_type: Mapped[str] = mapped_column(String(32), default="monte_carlo", nullable=False)
    params: Mapped[dict] = mapped_column(JSON, default=dict)
    result: Mapped[dict | None] = mapped_column(JSON, nullable=True)
    error: Mapped[str | None] = mapped_column(String(1024), nullable=True)
