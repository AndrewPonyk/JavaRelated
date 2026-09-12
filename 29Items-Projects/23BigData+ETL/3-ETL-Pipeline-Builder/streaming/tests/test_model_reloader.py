import json

from processor_fakes import FakeRedis

from processor.anomaly.detector import EwmaAnomalyDetector
from processor.model_reloader import ModelReloader


def warmed_detector(**kwargs) -> EwmaAnomalyDetector:
    detector = EwmaAnomalyDetector(alpha=0.05, z_threshold=4.0, warmup=20, **kwargs)
    for i in range(100):
        detector.score("m", 100.0 + (i % 3))
    return detector


async def test_reloader_applies_published_model_once_per_version():
    detector = warmed_detector()
    redis = FakeRedis()
    reloader = ModelReloader("redis://unused", detector, client=redis)

    assert await reloader.check_once() is False  # nothing published yet

    await redis.hset(
        "anomaly:model",
        mapping={"version": "v1", "params": json.dumps({"m": {"z_threshold": 100.0}})},
    )
    assert await reloader.check_once() is True
    assert reloader.current_version == "v1"
    assert await reloader.check_once() is False  # same version — no reapply


async def test_published_threshold_actually_changes_scoring():
    detector = warmed_detector()
    # ~z=490 spike: alerts with default threshold 4.0
    assert detector.score("m", 500.0).is_anomaly

    redis = FakeRedis()
    await redis.hset(
        "anomaly:model",
        mapping={"version": "v2", "params": json.dumps({"m": {"z_threshold": 10_000.0}})},
    )
    reloader = ModelReloader("redis://unused", detector, client=redis)
    await reloader.check_once()

    assert detector.score("m", 500.0).is_anomaly is False  # threshold raised


async def test_bad_params_json_keeps_current_model():
    detector = warmed_detector()
    redis = FakeRedis()
    await redis.hset("anomaly:model", mapping={"version": "v3", "params": "{broken"})
    reloader = ModelReloader("redis://unused", detector, client=redis)

    assert await reloader.check_once() is False
    assert reloader.current_version is None


def test_apply_overrides_ignores_invalid_values():
    detector = warmed_detector()
    detector.apply_overrides(
        {
            "m": {"z_threshold": -5, "alpha": 3.0, "warmup": "soon", "unknown": 1},
            "ok": {"z_threshold": 6.5, "warmup": 30},
        }
    )
    assert detector._overrides == {"ok": {"z_threshold": 6.5, "warmup": 30}}
