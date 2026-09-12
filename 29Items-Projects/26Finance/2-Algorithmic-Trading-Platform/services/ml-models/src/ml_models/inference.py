"""Inference service: consume ``market.bars``, publish ``predictions``.

Decoupled from strategy tick-rate via the bus so a slow model can lag without
stalling execution (ARCHITECTURE §2.4). Serves the lightweight
:class:`MomentumLogisticPredictor` by default.
"""

from __future__ import annotations

from collections import defaultdict, deque

import numpy as np

from ml_models.predictor import MomentumLogisticPredictor
from trading_common.messaging import MessageBus, topics
from trading_common.models.market import Bar, Prediction
from trading_common.utils import get_logger

log = get_logger(component="ml-inference")


class InferenceService:
    def __init__(
        self,
        bus: MessageBus,
        predictor: MomentumLogisticPredictor | None = None,
        *,
        window: int = 64,
        horizon_secs: int = 60,
    ) -> None:
        self._bus = bus
        self._predictor = predictor or MomentumLogisticPredictor()
        self._history: dict[str, deque[float]] = defaultdict(lambda: deque(maxlen=window))
        self._horizon = horizon_secs
        self.predictions_published = 0

    async def run(self) -> None:
        sub = self._bus.subscribe(topics.MARKET_BARS, Bar, group="ml-inference")
        async for bar in sub:
            await self._on_bar(bar)

    async def _on_bar(self, bar: Bar) -> None:
        hist = self._history[bar.symbol]
        hist.append(float(bar.close))
        if len(hist) < 7:  # need a minimum window to featurize
            return
        prob_up = self._predictor.predict_proba_up(np.array(hist))
        self.predictions_published += 1
        await self._bus.publish(
            topics.PREDICTIONS,
            Prediction(
                correlation_id=bar.correlation_id,
                symbol=bar.symbol,
                prob_up=prob_up,
                horizon_secs=self._horizon,
            ),
            key=bar.symbol,
        )
