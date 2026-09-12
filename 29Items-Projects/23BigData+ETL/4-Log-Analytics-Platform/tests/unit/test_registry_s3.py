"""S3 model registry against a boto3-shaped in-memory fake (raises real ClientError)."""

from __future__ import annotations

import io

import numpy as np
import pandas as pd
import pytest

pytest.importorskip("sklearn")
pytest.importorskip("botocore")

from botocore.exceptions import ClientError

from log_analytics.ml.features import FEATURE_COLUMNS
from log_analytics.ml.isolation_forest import LogAnomalyDetector, ModelMetadata
from log_analytics.ml.registry import S3ModelRegistry, get_registry


class FakeS3:
    def __init__(self) -> None:
        self.objects: dict[tuple[str, str], bytes] = {}

    def put_object(self, Bucket: str, Key: str, Body: bytes) -> dict:  # noqa: N803 - boto3 API shape
        self.objects[(Bucket, Key)] = Body
        return {}

    def get_object(self, Bucket: str, Key: str) -> dict:  # noqa: N803 - boto3 API shape
        if (Bucket, Key) not in self.objects:
            raise ClientError({"Error": {"Code": "NoSuchKey", "Message": Key}}, "GetObject")
        return {"Body": io.BytesIO(self.objects[(Bucket, Key)])}


@pytest.fixture()
def features() -> pd.DataFrame:
    rng = np.random.default_rng(3)
    return pd.DataFrame(
        rng.normal(100, 10, size=(150, len(FEATURE_COLUMNS))), columns=FEATURE_COLUMNS
    )


def test_uri_parsing_rejects_garbage() -> None:
    with pytest.raises(ValueError):
        S3ModelRegistry("not-s3://x", client=FakeS3())
    with pytest.raises(ValueError):
        S3ModelRegistry("s3://", client=FakeS3())


def test_empty_registry_has_no_latest() -> None:
    registry = S3ModelRegistry("s3://models-bucket/anomaly", client=FakeS3())
    assert registry.latest_version() is None
    with pytest.raises(FileNotFoundError):
        registry.load()


def test_save_load_roundtrip_with_prefix(features: pd.DataFrame) -> None:
    fake = FakeS3()
    registry = S3ModelRegistry("s3://models-bucket/anomaly/", client=fake)
    detector = LogAnomalyDetector(random_state=1).fit(features)

    version = registry.save(detector, ModelMetadata(version="20260708-010203", n_samples=150))
    assert registry.latest_version() == version
    assert ("models-bucket", "anomaly/20260708-010203/model.joblib") in fake.objects
    assert ("models-bucket", "anomaly/latest.json") in fake.objects

    loaded, metadata = registry.load()
    assert metadata.version == version
    np.testing.assert_allclose(loaded.score(features), detector.score(features))


def test_get_registry_dispatches_by_scheme(tmp_path) -> None:
    from log_analytics.ml.registry import LocalModelRegistry

    assert isinstance(get_registry(str(tmp_path)), LocalModelRegistry)
    assert isinstance(get_registry("s3://b/p", s3_client=FakeS3()), S3ModelRegistry)
