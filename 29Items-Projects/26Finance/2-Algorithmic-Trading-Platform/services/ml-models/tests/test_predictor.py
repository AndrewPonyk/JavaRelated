"""Tests for the NumPy momentum predictor and the inference service."""

from __future__ import annotations

import asyncio
import contextlib
from decimal import Decimal

import numpy as np
import pytest
from ml_models.inference import InferenceService
from ml_models.predictor import MomentumLogisticPredictor, featurize

from trading_common.messaging import InMemoryBus, topics
from trading_common.models.market import Bar, Prediction


def test_featurize_short_window_is_zero() -> None:
    assert not featurize(np.array([1.0, 2.0, 3.0])).any()


def test_featurize_shape() -> None:
    feats = featurize(np.arange(1, 20, dtype=float))
    assert feats.shape == (5,)
    assert feats[0] == 1.0  # bias term


def test_uptrend_predicts_up() -> None:
    closes = np.linspace(100, 120, 30)
    assert MomentumLogisticPredictor().predict_proba_up(closes) > 0.5


def test_downtrend_predicts_down() -> None:
    closes = np.linspace(120, 100, 30)
    assert MomentumLogisticPredictor().predict_proba_up(closes) < 0.5


def test_training_improves_separation() -> None:
    rng = np.random.default_rng(0)
    # trending series with noise
    series = np.cumsum(rng.normal(0.1, 1.0, size=500)) + 200
    windows, labels = MomentumLogisticPredictor.make_training_set(series, window=30)
    model = MomentumLogisticPredictor(weights=np.zeros(5)).fit(windows, labels, epochs=300)
    # after training, predictions correlate with realized direction better than chance
    preds = [model.predict_proba_up(w) > 0.5 for w in windows]
    acc = np.mean([p == bool(y) for p, y in zip(preds, labels, strict=False)])
    assert acc > 0.5


@pytest.mark.asyncio
async def test_inference_service_publishes_predictions() -> None:
    bus = InMemoryBus()
    await bus.start()
    sub = bus.subscribe(topics.PREDICTIONS, Prediction, group="t")
    svc = InferenceService(bus)
    run = asyncio.ensure_future(svc.run())
    await asyncio.sleep(0)

    for px in range(100, 115):
        c = Decimal(str(px))
        await bus.publish(
            topics.MARKET_BARS, Bar(symbol="AAPL", open=c, high=c, low=c, close=c, volume=1)
        )
    await asyncio.sleep(0.05)

    pred = await asyncio.wait_for(anext(aiter(sub)), timeout=1.0)
    assert pred.symbol == "AAPL"
    assert 0.0 <= pred.prob_up <= 1.0
    assert svc.predictions_published >= 1
    await bus.stop()
    with contextlib.suppress(asyncio.CancelledError):
        await run
