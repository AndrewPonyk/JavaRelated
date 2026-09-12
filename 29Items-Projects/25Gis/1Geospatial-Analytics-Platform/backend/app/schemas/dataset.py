from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

VALID_GEOMETRY_TYPES = {"Point", "LineString", "Polygon", "MultiPoint", "MultiLineString", "MultiPolygon"}


def validate_geojson_geometry(value: dict) -> dict:
    geometry_type = value.get("type")
    coordinates = value.get("coordinates")
    if geometry_type not in VALID_GEOMETRY_TYPES:
        raise ValueError(f"Geometry type must be one of {sorted(VALID_GEOMETRY_TYPES)}")
    if coordinates is None:
        raise ValueError("GeoJSON geometry requires coordinates")
    return value


class DatasetBase(BaseModel):
    name: str = Field(min_length=1, max_length=160)
    description: str | None = Field(default=None, max_length=2_000)
    source_type: str = Field(default="vector", pattern="^(vector|raster|classification)$")
    metadata_json: dict = Field(default_factory=dict)


class DatasetCreate(DatasetBase):
    pass


class DatasetUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=160)
    description: str | None = Field(default=None, max_length=2_000)
    metadata_json: dict | None = None


class DatasetRead(DatasetBase):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    created_at: datetime | None = None
    updated_at: datetime | None = None


class DatasetFeatureBase(BaseModel):
    properties: dict = Field(default_factory=dict)
    geometry: dict

    @field_validator("geometry")
    @classmethod
    def geometry_must_be_geojson(cls, value: dict) -> dict:
        return validate_geojson_geometry(value)


class DatasetFeatureCreate(DatasetFeatureBase):
    pass


class DatasetFeatureUpdate(BaseModel):
    properties: dict | None = None
    geometry: dict | None = None

    @model_validator(mode="after")
    def must_update_at_least_one_field(self) -> "DatasetFeatureUpdate":
        if self.properties is None and self.geometry is None:
            raise ValueError("At least one field must be provided")
        return self

    @field_validator("geometry")
    @classmethod
    def optional_geometry_must_be_geojson(cls, value: dict | None) -> dict | None:
        if value is None:
            return None
        return validate_geojson_geometry(value)


class DatasetFeatureRead(DatasetFeatureBase):
    id: UUID
    dataset_id: UUID
    created_at: datetime | None = None


class GeoJsonFeatureCollection(BaseModel):
    type: str = Field(pattern="^FeatureCollection$")
    features: list[dict] = Field(min_length=1)


class LayerBase(BaseModel):
    dataset_id: UUID
    name: str = Field(min_length=1, max_length=160)
    layer_type: str = Field(default="wms", pattern="^(wms|wfs|deckgl|classification)$")
    style: str = Field(default="default", min_length=1, max_length=120)
    is_public: bool = False


class LayerCreate(LayerBase):
    pass


class LayerUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=160)
    layer_type: str | None = Field(default=None, pattern="^(wms|wfs|deckgl|classification)$")
    style: str | None = Field(default=None, min_length=1, max_length=120)
    is_public: bool | None = None


class LayerRead(LayerBase):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    created_at: datetime | None = None
    updated_at: datetime | None = None


class ClassificationJobCreate(BaseModel):
    dataset_id: UUID
    model_version: str = Field(default="rules-v1", min_length=1, max_length=80)


class ClassificationJobUpdate(BaseModel):
    model_version: str | None = Field(default=None, min_length=1, max_length=80)


class ClassificationJobRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    dataset_id: UUID | None = None
    status: str
    model_version: str | None = None
    metrics: dict = Field(default_factory=dict)
    artifact_uri: str | None = None
    error_message: str | None = None
    created_at: datetime | None = None
    updated_at: datetime | None = None


class BoundingBoxQuery(BaseModel):
    min_x: float
    min_y: float
    max_x: float
    max_y: float
    srid: int = 4326

    @field_validator("max_x")
    @classmethod
    def max_x_must_be_valid(cls, value: float, info) -> float:
        min_x = info.data.get("min_x")
        if min_x is not None and value <= min_x:
            raise ValueError("max_x must be greater than min_x")
        return value

    @field_validator("max_y")
    @classmethod
    def max_y_must_be_valid(cls, value: float, info) -> float:
        min_y = info.data.get("min_y")
        if min_y is not None and value <= min_y:
            raise ValueError("max_y must be greater than min_y")
        return value


class ProximityQuery(BaseModel):
    longitude: float = Field(ge=-180, le=180)
    latitude: float = Field(ge=-90, le=90)
    radius_meters: float = Field(gt=0, le=100_000)


class DatasetSummary(BaseModel):
    dataset_id: UUID
    feature_count: int
    extent: dict | None
    classes: dict[str, int]
