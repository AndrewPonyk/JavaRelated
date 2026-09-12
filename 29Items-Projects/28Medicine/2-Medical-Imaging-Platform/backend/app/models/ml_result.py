"""MlResult — output of a diagnostic-assist model for one instance.

Decision support only. Every row records the model id + version so predictions
are reproducible and auditable.
"""

from __future__ import annotations

import uuid
from typing import TYPE_CHECKING

from sqlalchemy import Float, ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import GUID, Base, JSONType, TimestampMixin, UUIDMixin

if TYPE_CHECKING:
    from app.models.instance import Instance


class MlResult(UUIDMixin, TimestampMixin, Base):
    __tablename__ = "ml_results"

    model_name: Mapped[str] = mapped_column(String(128))  # e.g. "chexnet"
    model_version: Mapped[str] = mapped_column(String(64))  # weights/version tag
    # Per-pathology probabilities, e.g. {"Pneumonia": 0.83, "Cardiomegaly": 0.10}.
    predictions: Mapped[dict[str, float]] = mapped_column(JSONType)
    top_label: Mapped[str | None] = mapped_column(String(128))
    top_score: Mapped[float | None] = mapped_column(Float)
    # Object key of the class-activation map / heatmap overlay (optional).
    heatmap_key: Mapped[str | None] = mapped_column(String(512))

    instance_pk: Mapped[uuid.UUID] = mapped_column(
        GUID, ForeignKey("instances.id", ondelete="CASCADE"), index=True
    )
    instance: Mapped[Instance] = relationship(back_populates="ml_results")
