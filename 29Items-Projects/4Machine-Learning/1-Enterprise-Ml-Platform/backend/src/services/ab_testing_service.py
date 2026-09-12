"""A/B testing: traffic splitting, stable bucketing, significance testing.

Test *definitions* are durable (repository); raw outcome *samples* are
high-volume telemetry held in the in-memory buffer (Redis/metrics store in prod).
"""
from __future__ import annotations

import hashlib
import logging

from scipy.stats import ttest_ind

from src.core.errors import ConflictError, NotFoundError, UnprocessableError
from src.db.models import ABTest, new_id
from src.db.repository import SqlAlchemyRepository
from src.db.telemetry import TelemetryBuffer, get_telemetry

logger = logging.getLogger(__name__)


class ABTestingService:
    """Assign variants and evaluate champion vs. challenger performance."""

    def __init__(
        self, repo: SqlAlchemyRepository, telemetry: TelemetryBuffer | None = None
    ) -> None:
        self._repo = repo
        self._tel = telemetry or get_telemetry()

    async def create_test(self, config: dict) -> str:
        """Persist an A/B test definition and return its id."""
        model_name = config["model_name"]
        if config["champion_version"] == config["challenger_version"]:
            raise UnprocessableError("champion and challenger versions must differ")
        if await self._repo.active_ab_test(model_name) is not None:
            raise ConflictError(f"an active A/B test already exists for {model_name}")
        test = ABTest(
            id=new_id(),
            model_name=model_name,
            champion_version=config["champion_version"],
            challenger_version=config["challenger_version"],
            challenger_traffic_pct=config.get("challenger_traffic_pct", 10.0),
        )
        await self._repo.add_ab_test(test)
        logger.info("created ab-test id=%s model=%s", test.id, model_name)
        return test.id

    async def resolve_variant(self, model_name: str, subject_id: str | None) -> str:
        """Deterministically bucket a subject into champion/challenger.

        Hashing ``subject_id`` keeps assignment stable across requests. No active
        test, or no subject, deterministically resolves to champion.
        """
        test = await self._repo.active_ab_test(model_name)
        if test is None or subject_id is None:
            return "champion"
        bucket = int(hashlib.sha256(subject_id.encode()).hexdigest(), 16) % 100
        return "challenger" if bucket < test.challenger_traffic_pct else "champion"

    async def record_outcome(self, model_name: str, variant: str, value: float) -> None:
        """Capture a metric observation for the active test (best-effort)."""
        if await self._repo.active_ab_test(model_name) is None:
            return
        self._tel.record_ab_outcome(model_name, variant, value)

    async def evaluate(self, test_id: str) -> dict:
        """Compute the significance of challenger vs. champion via a t-test."""
        test = await self._repo.get_ab_test(test_id)
        if test is None:
            raise NotFoundError(f"ab-test not found: {test_id}")

        champ, chall = self._tel.ab_outcomes(test.model_name)
        if len(champ) < 2 or len(chall) < 2:
            # Not enough samples to test — report inconclusive rather than crash.
            return _result(test_id, test.model_name, champ, chall, p_value=1.0, sig=False)

        p_value = float(ttest_ind(chall, champ, equal_var=False).pvalue)
        return _result(test_id, test.model_name, champ, chall, p_value, p_value < 0.05)


def _mean(values: list[float]) -> float:
    return round(sum(values) / len(values), 6) if values else 0.0


def _result(
    test_id: str,
    model_name: str,
    champ: list[float],
    chall: list[float],
    p_value: float,
    sig: bool,
) -> dict:
    return {
        "test_id": test_id,
        "model_name": model_name,
        "champion_metric": _mean(champ),
        "challenger_metric": _mean(chall),
        "p_value": p_value,
        "significant": sig,
    }
