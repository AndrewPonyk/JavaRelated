"""Tests for graph feature extraction."""

from app.ml.features import FeatureExtractor, _cosine
from tests.fakes import FakeDriver, FakeRecord, FakeResult


def test_cosine_identical():
    assert abs(_cosine([1, 0], [1, 0]) - 1.0) < 1e-9


def test_cosine_orthogonal():
    assert _cosine([1, 0], [0, 1]) == 0.0


def test_cosine_handles_none():
    assert _cosine(None, [1, 2]) == 0.0


def test_cosine_handles_length_mismatch():
    assert _cosine([1, 2], [1]) == 0.0


def test_cosine_handles_zero_vector():
    assert _cosine([0, 0], [0, 0]) == 0.0


async def test_extract_returns_features():
    record = FakeRecord(
        {"shared_class_count": 2, "graph_distance": 1, "emb_a": [1, 0], "emb_b": [1, 0]}
    )
    extractor = FeatureExtractor(FakeDriver(FakeResult(single=record)))
    features = await extractor.extract("1", "2")
    assert features.shared_class_count == 2
    assert features.graph_distance == 1
    assert abs(features.embedding_cosine - 1.0) < 1e-9
    assert features.to_vector() == [2.0, 1.0, features.embedding_cosine]


async def test_extract_handles_missing_node():
    extractor = FeatureExtractor(FakeDriver(FakeResult(single=None)))
    features = await extractor.extract("1", "2")
    assert features.shared_class_count == 0
    assert features.graph_distance == -1
