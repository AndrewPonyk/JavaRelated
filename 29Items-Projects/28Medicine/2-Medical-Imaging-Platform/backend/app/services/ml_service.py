"""ML diagnostic-assist service: call inference, persist + query results.

The heavy model runs in a separate service (GPU profile). This module is the
client + persistence layer used by the API/worker. ML failures are non-fatal to
ingestion — a missing prediction must never lose an archived study.
"""

from __future__ import annotations

import uuid

import httpx
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.logging import get_logger
from app.models.instance import Instance
from app.models.ml_result import MlResult
from app.models.series import Series
from app.models.study import Study
from app.services.queue_service import get_queue_client

log = get_logger(__name__)


class MlService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    def enqueue(self, instance: Instance) -> None:
        """Push an inference job for an asynchronous ML worker to consume."""
        get_queue_client().send(
            {
                "type": "inference",
                "sop_instance_uid": instance.sop_instance_uid,
                "object_key": instance.object_key,
                "bucket": settings.s3_bucket_dicom,
            }
        )

    async def infer_and_store(self, instance: Instance) -> MlResult | None:
        """Call the inference service for one instance and persist the result.

        Returns None (and logs) on any ML-side failure — ingestion continues.
        """
        payload = {
            "sop_instance_uid": instance.sop_instance_uid,
            "object_key": instance.object_key,
        }
        try:
            async with httpx.AsyncClient(base_url=settings.ml_service_url, timeout=30) as client:
                resp = await client.post("/v1/predict", json=payload)
                resp.raise_for_status()
                data = resp.json()
        except (httpx.HTTPError, ValueError) as exc:
            log.warning(
                "ml.infer_failed",
                sop_instance_uid=instance.sop_instance_uid,
                error=str(exc),
            )
            return None

        return await self.store_result(
            instance_pk=instance.id,
            model_name=data["model_name"],
            model_version=data["model_version"],
            predictions=data["predictions"],
            top_label=data.get("top_label"),
            top_score=data.get("top_score"),
        )

    async def store_result(
        self,
        *,
        instance_pk: uuid.UUID,
        model_name: str,
        model_version: str,
        predictions: dict[str, float],
        top_label: str | None,
        top_score: float | None,
        heatmap_key: str | None = None,
    ) -> MlResult:
        result = MlResult(
            instance_pk=instance_pk,
            model_name=model_name,
            model_version=model_version,
            predictions=predictions,
            top_label=top_label,
            top_score=top_score,
            heatmap_key=heatmap_key,
        )
        self._session.add(result)
        await self._session.flush()
        return result

    async def list_for_study(self, study_instance_uid: str) -> list[MlResult]:
        stmt = (
            select(MlResult)
            .join(Instance, MlResult.instance_pk == Instance.id)
            .join(Series, Instance.series_pk == Series.id)
            .join(Study, Series.study_pk == Study.id)
            .where(Study.study_instance_uid == study_instance_uid)
            .order_by(MlResult.created_at.desc())
        )
        result = await self._session.execute(stmt)
        return list(result.scalars().all())

    async def get_for_instance(self, instance_pk: uuid.UUID) -> MlResult | None:
        result = await self._session.execute(
            select(MlResult)
            .where(MlResult.instance_pk == instance_pk)
            .order_by(MlResult.created_at.desc())
        )
        return result.scalars().first()
