"""Celery tasks orchestrating ML eligibility screening.

The Django-facing seam over the portable NLP pipeline in ``ml/``. Idempotent;
runs on the dedicated ``ml`` queue so a backlog cannot starve time-sensitive work.
"""
from __future__ import annotations

import logging

from celery import shared_task
from django.conf import settings

from ml.pipelines.eligibility_screening import EligibilityScreener, ScreeningInput

from .models import Screening, ScreeningStatus

logger = logging.getLogger(__name__)


def _build_criteria(screening: Screening) -> list[dict]:
    """Collect the protocol's eligibility criteria as pipeline inputs."""
    protocol = screening.study.protocol
    return [
        {
            "id": str(c.public_id),
            "type": c.type,
            "text": c.text,
            "coded_rule": c.coded_rule or {},
        }
        for c in protocol.criteria.all()
    ]


@shared_task(
    name="apps.eligibility.tasks.screen_candidate",
    bind=True,
    max_retries=3,
    default_retry_delay=30,
    acks_late=True,
)
def screen_candidate(self, screening_id: int) -> dict:
    """Run NLP screening for one Screening. Pass IDs, not ORM objects (TECH-NOTES §3.6)."""
    try:
        screening = Screening.objects.select_related("study__protocol", "subject").get(
            pk=screening_id
        )
    except Screening.DoesNotExist:
        logger.warning("screen_candidate: screening %s vanished", screening_id)
        return {"status": "missing"}

    if screening.status not in {ScreeningStatus.PENDING, ScreeningStatus.FAILED}:
        return {"status": "skipped", "current": screening.status}

    try:
        screener = EligibilityScreener.from_settings(settings)
        result = screener.run(
            ScreeningInput(
                note_text=screening.note_text or "",
                criteria=_build_criteria(screening),
            )
        )
    except Exception as exc:  # noqa: BLE001 — bounded retry, then FAILED
        logger.exception("screen_candidate failed for %s", screening_id)
        if self.request.retries < self.max_retries:
            raise self.retry(exc=exc) from exc
        screening.status = ScreeningStatus.FAILED
        screening.save(update_fields=["status", "updated_at"])
        return {"status": "failed"}

    screening.ml_score = result.score
    screening.ml_recommendation = result.recommendation
    screening.rationale = result.rationale
    screening.model_version = result.model_version
    screening.ontology_versions = result.ontology_versions
    screening.status = ScreeningStatus.AWAITING_REVIEW
    screening.save()
    logger.info(
        "Screening %s -> AWAITING_REVIEW (score=%.2f, rec=%s)",
        screening_id,
        result.score,
        result.recommendation,
    )
    return {"status": "ok", "score": result.score, "recommendation": result.recommendation}


@shared_task(name="apps.eligibility.tasks.rescreen_pending_candidates")
def rescreen_pending_candidates() -> dict:
    """Re-enqueue screening for candidates still pending (e.g. new EHR data)."""
    ids = list(
        Screening.objects.filter(status=ScreeningStatus.PENDING).values_list("id", flat=True)
    )
    for sid in ids:
        screen_candidate.delay(sid)
    return {"requeued": len(ids)}
