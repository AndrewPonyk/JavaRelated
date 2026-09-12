from datetime import datetime
from uuid import UUID as PyUUID
from uuid import uuid4

from geoalchemy2 import Geometry
from sqlalchemy import DateTime, ForeignKey, String, Text, func
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.session import Base


class Dataset(Base):
    __tablename__ = "datasets"

    id: Mapped[PyUUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    name: Mapped[str] = mapped_column(String(160), nullable=False, index=True)
    description: Mapped[str | None] = mapped_column(Text)
    source_type: Mapped[str] = mapped_column(String(50), nullable=False, default="vector")
    metadata_json: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())

    features: Mapped[list["DatasetFeature"]] = relationship(
        back_populates="dataset",
        cascade="all, delete-orphan",
        passive_deletes=True,
    )
    layers: Mapped[list["Layer"]] = relationship(
        back_populates="dataset",
        cascade="all, delete-orphan",
        passive_deletes=True,
    )
    classification_jobs: Mapped[list["MLClassificationJob"]] = relationship(back_populates="dataset")


class DatasetFeature(Base):
    __tablename__ = "dataset_features"

    id: Mapped[PyUUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    dataset_id: Mapped[PyUUID] = mapped_column(UUID(as_uuid=True), ForeignKey("datasets.id", ondelete="CASCADE"), nullable=False, index=True)
    properties: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
    geom: Mapped[object] = mapped_column(Geometry(geometry_type="GEOMETRY", srid=4326, spatial_index=True), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    dataset: Mapped[Dataset] = relationship(
        back_populates="features",
    )


class Layer(Base):
    __tablename__ = "layers"

    id: Mapped[PyUUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    dataset_id: Mapped[PyUUID] = mapped_column(UUID(as_uuid=True), ForeignKey("datasets.id", ondelete="CASCADE"), nullable=False, index=True)
    name: Mapped[str] = mapped_column(String(160), nullable=False)
    layer_type: Mapped[str] = mapped_column(String(40), nullable=False, default="wms")
    style: Mapped[str] = mapped_column(String(120), nullable=False, default="default")
    is_public: Mapped[bool] = mapped_column(default=False, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())

    dataset: Mapped[Dataset] = relationship(
        back_populates="layers",
    )


class MLClassificationJob(Base):
    __tablename__ = "ml_classification_jobs"

    id: Mapped[PyUUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    dataset_id: Mapped[PyUUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("datasets.id", ondelete="SET NULL"), nullable=True, index=True)
    status: Mapped[str] = mapped_column(String(40), nullable=False, default="queued")
    model_version: Mapped[str | None] = mapped_column(String(80))
    metrics: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
    artifact_uri: Mapped[str | None] = mapped_column(Text)
    error_message: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())

    dataset: Mapped[Dataset | None] = relationship(
        back_populates="classification_jobs",
    )
