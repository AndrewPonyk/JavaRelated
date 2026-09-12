"""Async repository over the platform metadata tables.

The single seam between services and SQLAlchemy. Services depend on this, never
on sessions or ``select`` directly, so storage can evolve (or be mocked) without
touching business logic. Writes are flushed (so server defaults populate) but
committed by the request-scoped session dependency.
"""
from __future__ import annotations

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from src.db.models import ABTest, DriftReport, Experiment, ModelVersion, Run


class SqlAlchemyRepository:
    """Persistence operations for the platform's durable entities."""

    def __init__(self, session: AsyncSession) -> None:
        self._s = session

    # -- experiments ------------------------------------------------------
    async def add_experiment(self, exp: Experiment) -> Experiment:
        self._s.add(exp)
        await self._s.flush()
        return exp

    async def experiment_name_exists(self, name: str) -> bool:
        result = await self._s.scalar(
            select(func.count()).select_from(Experiment).where(Experiment.name == name)
        )
        return bool(result)

    async def list_experiments(self, limit: int, offset: int) -> tuple[list[Experiment], int]:
        total = await self._s.scalar(select(func.count()).select_from(Experiment)) or 0
        rows = (
            await self._s.scalars(
                select(Experiment)
                .order_by(Experiment.created_at)
                .limit(limit)
                .offset(offset)
            )
        ).all()
        return list(rows), int(total)

    async def get_experiment(self, experiment_id: str) -> Experiment | None:
        return await self._s.get(Experiment, experiment_id)

    # -- runs -------------------------------------------------------------
    async def add_run(self, run: Run) -> Run:
        self._s.add(run)
        await self._s.flush()
        return run

    # -- model versions ---------------------------------------------------
    async def next_model_version(self, name: str) -> int:
        current = await self._s.scalar(
            select(func.max(ModelVersion.version)).where(ModelVersion.name == name)
        )
        return int(current or 0) + 1

    async def add_model_version(self, mv: ModelVersion) -> ModelVersion:
        self._s.add(mv)
        await self._s.flush()
        return mv

    async def get_model_version(self, name: str, version: int) -> ModelVersion | None:
        return await self._s.get(ModelVersion, (name, version))

    async def list_model_versions(self, name: str) -> list[ModelVersion]:
        rows = (
            await self._s.scalars(
                select(ModelVersion)
                .where(ModelVersion.name == name)
                .order_by(ModelVersion.version)
            )
        ).all()
        return list(rows)

    async def production_version(self, name: str) -> ModelVersion | None:
        """Current Production version, or the latest registered as fallback champion."""
        prod = await self._s.scalar(
            select(ModelVersion)
            .where(ModelVersion.name == name, ModelVersion.stage == "Production")
            .order_by(ModelVersion.version.desc())
            .limit(1)
        )
        if prod is not None:
            return prod
        return await self._s.scalar(
            select(ModelVersion)
            .where(ModelVersion.name == name)
            .order_by(ModelVersion.version.desc())
            .limit(1)
        )

    # -- A/B tests --------------------------------------------------------
    async def add_ab_test(self, test: ABTest) -> ABTest:
        self._s.add(test)
        await self._s.flush()
        return test

    async def get_ab_test(self, test_id: str) -> ABTest | None:
        return await self._s.get(ABTest, test_id)

    async def active_ab_test(self, model_name: str) -> ABTest | None:
        return await self._s.scalar(
            select(ABTest)
            .where(ABTest.model_name == model_name, ABTest.is_active.is_(True))
            .order_by(ABTest.created_at.desc())
            .limit(1)
        )

    # -- drift ------------------------------------------------------------
    async def add_drift_report(self, report: DriftReport) -> DriftReport:
        self._s.add(report)
        await self._s.flush()
        return report

    async def latest_drift_report(self, model_name: str) -> DriftReport | None:
        return await self._s.scalar(
            select(DriftReport)
            .where(DriftReport.model_name == model_name)
            .order_by(DriftReport.evaluated_at.desc())
            .limit(1)
        )
