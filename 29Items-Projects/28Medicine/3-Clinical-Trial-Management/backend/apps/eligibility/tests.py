from __future__ import annotations

import pytest

from apps.eligibility import services
from apps.eligibility.models import Decision, Screening, ScreeningStatus
from apps.trials.models import CriterionType, EligibilityCriterion

pytestmark = pytest.mark.django_db


@pytest.fixture
def study_with_criteria(open_study):
    proto = open_study.protocol
    EligibilityCriterion.objects.create(
        protocol=proto,
        type=CriterionType.INCLUSION,
        text="Has diabetes",
        coded_rule={"keywords": ["diabetes"], "comparator": "present"},
    )
    EligibilityCriterion.objects.create(
        protocol=proto,
        type=CriterionType.EXCLUSION,
        text="Pregnant",
        coded_rule={"keywords": ["pregnant"], "comparator": "present"},
    )
    return open_study


def test_screen_candidate_populates_result(study_with_criteria, subject):
    screening = Screening.objects.create(
        study=study_with_criteria,
        subject=subject,
        note_text="Type 2 diabetes, denies pregnancy.",
    )
    from apps.eligibility.tasks import screen_candidate

    screen_candidate(screening.pk)
    screening.refresh_from_db()
    assert screening.status == ScreeningStatus.AWAITING_REVIEW
    assert screening.ml_recommendation == Decision.ELIGIBLE
    assert screening.rationale  # per-criterion evidence recorded
    assert screening.model_version


def test_create_screening_runs_async(
    study_with_criteria, subject, crc, django_capture_on_commit_callbacks
):
    with django_capture_on_commit_callbacks(execute=True):
        screening = services.create_screening(
            study=study_with_criteria,
            subject=subject,
            note_text="diabetes; denies pregnancy",
            created_by=crc,
        )
    screening.refresh_from_db()
    assert screening.status == ScreeningStatus.AWAITING_REVIEW


def test_record_decision_is_authoritative_and_signed(study_with_criteria, subject, pi):
    screening = Screening.objects.create(
        study=study_with_criteria,
        subject=subject,
        status=ScreeningStatus.AWAITING_REVIEW,
        ml_recommendation=Decision.ELIGIBLE,
    )
    services.record_decision(screening=screening, decision=Decision.INELIGIBLE, decided_by=pi)
    screening.refresh_from_db()
    # Human decision governs even when it overrides the ML recommendation.
    assert screening.status == ScreeningStatus.DECIDED
    assert screening.human_decision == Decision.INELIGIBLE
    from apps.audit.models import AuditEvent

    assert AuditEvent.objects.filter(action="ESIGN", entity_type="eligibility.Screening").exists()


def test_note_text_is_write_only_in_api(api, study_with_criteria, subject, crc):
    api.force_authenticate(user=crc)
    resp = api.post(
        "/api/v1/screenings/",
        {
            "study": str(study_with_criteria.public_id),
            "subject": str(subject.public_id),
            "note_text": "diabetes",
        },
        format="json",
    )
    assert resp.status_code == 201
    assert "note_text" not in resp.data  # PHI never echoed back
