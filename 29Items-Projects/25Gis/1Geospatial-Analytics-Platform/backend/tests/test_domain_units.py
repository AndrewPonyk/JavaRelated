from pathlib import Path

import pytest
from pydantic import ValidationError

from app.core.config import Settings
from app.ml.land_use_classifier import LandUseClassifier
from app.schemas.dataset import DatasetFeatureCreate, DatasetFeatureUpdate
from app.services.spatial_analysis import BoundingBox


def test_geojson_geometry_validation_rejects_invalid_types() -> None:
    with pytest.raises(ValidationError):
        DatasetFeatureCreate(properties={}, geometry={"type": "Circle", "coordinates": [0, 0]})


def test_feature_update_requires_a_field() -> None:
    with pytest.raises(ValidationError):
        DatasetFeatureUpdate()


def test_auth_enabled_requires_strong_jwt_secret() -> None:
    with pytest.raises(ValidationError):
        Settings(auth_enabled=True, jwt_secret="short")


def test_bounding_box_dataclass_keeps_query_values() -> None:
    bbox = BoundingBox(min_x=1, min_y=2, max_x=3, max_y=4)

    assert bbox.srid == 4326
    assert bbox.max_x == 3


def test_land_use_classifier_is_deterministic(tmp_path: Path) -> None:
    raster = tmp_path / "sample-raster.bin"
    raster.write_bytes(b"deterministic raster fixture")

    classifier = LandUseClassifier(model_path=tmp_path / "model.joblib")

    first = classifier.classify(raster)
    second = classifier.classify(raster)

    assert first == second
    assert round(sum(first.values()), 3) == 1.0
