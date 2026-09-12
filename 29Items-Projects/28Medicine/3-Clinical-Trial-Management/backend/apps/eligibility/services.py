"""Eligibility services: create a screening and record the authoritative
human decision.

The ML output is advisory; ``record_decision`` captures the human, e-signed
decision that governs (clinical safety + 21 CFR Part 11).
"""
from __future__ import annotations

from django.db import transaction
from django.utils import timezone

from apps.accounts.services import sign
from apps.common.exceptions import EligibilityError

from .models import Decision, Screening, ScreeningStatus
from .tasks import screen_candidate

ENTITY = "eligibility.Screening"


@transaction.atomic
def create_screening(*, study, subject, note_text: str = "", created_by=None) -> Screening:
    screening = Screening.objects.create(
        study=study,
        subject=subject,
        note_text=note_text,
        status=ScreeningStatus.PENDING,
        created_by=created_by,
    )
    # Enqueue async ML screening (runs inline under CELERY_TASK_ALWAYS_EAGER in tests).
    transaction.on_commit(lambda: screen_candidate.delay(screening.pk))
    return screening


@transaction.atomic
def record_decision(
    *, screening: Screening, decision: str, decided_by, password: str | None = None
) -> Screening:
    if decision not in Decision.values:
        raise EligibilityError(f"Unknown decision '{decision}'.")
    if screening.status not in {ScreeningStatus.AWAITING_REVIEW, ScreeningStatus.DECIDED}:
        raise EligibilityError("Screening is not ready for a decision.")

    screening.human_decision = decision
    screening.decided_by = decided_by
    screening.decided_at = timezone.now()
    screening.status = ScreeningStatus.DECIDED
    screening.save(
        update_fields=["human_decision", "decided_by", "decided_at", "status", "updated_at"]
    )
    sign(
        signer=decided_by,
        meaning=f"Eligibility decision: {decision}",
        entity_type=ENTITY,
        entity_id=screening.pk,
        password=password,
    )
    return screening
