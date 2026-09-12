"""Unit tests for the ML inference entrypoints (fake driver)."""

import app.ml.predict as predict_mod
from app.ml.model import SeverityModel
from tests.fakes import FakeDriver, FakeRecord, FakeResult


def _features_record(shared, distance):
    return FakeRecord(
        {"shared_class_count": shared, "graph_distance": distance, "emb_a": None, "emb_b": None}
    )


async def test_predict_severity(monkeypatch):
    SeverityModel.reset()
    record = _features_record(3, 1)
    monkeypatch.setattr(predict_mod, "get_driver", lambda: FakeDriver(FakeResult(single=record)))
    out = await predict_mod.predict_severity("1", "2")
    assert out["rxcui_a"] == "1"
    assert out["rxcui_b"] == "2"
    assert 0.0 <= out["confidence"] <= 1.0
    assert out["severity"] in {"minor", "moderate", "major", "contraindicated", "unknown"}


async def test_predict_severity_batch(monkeypatch):
    SeverityModel.reset()
    record = _features_record(0, -1)
    monkeypatch.setattr(predict_mod, "get_driver", lambda: FakeDriver(FakeResult(single=record)))
    out = await predict_mod.predict_severity_batch([("1", "2"), ("3", "4")])
    assert len(out) == 2


async def test_predict_severity_batch_empty():
    assert await predict_mod.predict_severity_batch([]) == []


async def test_low_confidence_becomes_unknown(monkeypatch):
    SeverityModel.reset()
    record = _features_record(3, 1)
    monkeypatch.setattr(predict_mod, "get_driver", lambda: FakeDriver(FakeResult(single=record)))
    settings = predict_mod.get_settings()
    original = settings.ml_confidence_floor
    settings.ml_confidence_floor = 1.0  # force everything below the floor
    try:
        out = await predict_mod.predict_severity("1", "2")
        assert out["severity"] == "unknown"
    finally:
        settings.ml_confidence_floor = original
