from __future__ import annotations

import pytest
from django.db import connection

from apps.patients import services
from apps.patients.models import Subject

pytestmark = pytest.mark.django_db


def test_phi_is_encrypted_at_rest():
    subject = services.create_subject(first_name="Jane", last_name="Doe")
    # Model access transparently decrypts.
    assert Subject.objects.get(pk=subject.pk).first_name == "Jane"
    # The raw stored column must NOT contain the plaintext.
    with connection.cursor() as cur:
        cur.execute("SELECT first_name FROM patients_subject WHERE id = %s", [subject.pk])
        raw = cur.fetchone()[0]
    assert raw != "Jane"
    assert "Jane" not in raw


def test_subject_code_is_generated_and_unique():
    s1 = services.create_subject(first_name="A")
    s2 = services.create_subject(first_name="B")
    assert s1.subject_code != s2.subject_code
    assert s1.subject_code.startswith("S-")


def test_deidentify_contains_no_phi():
    subject = services.create_subject(
        first_name="Jane", last_name="Doe", date_of_birth="1980-05-01", sex_at_birth="F"
    )
    deid = services.deidentify(subject)
    assert deid["subject_code"] == subject.subject_code
    assert "first_name" not in deid and "last_name" not in deid
    assert isinstance(deid["age"], int) and deid["age"] >= 40


def test_age_derivation_handles_missing_dob():
    subject = services.create_subject(first_name="No", last_name="Dob")
    assert subject.age is None


def test_phi_redacted_for_non_clinical_role(api, subject, monitor, crc):
    url = f"/api/v1/subjects/{subject.public_id}/"

    api.force_authenticate(user=monitor)  # MONITOR: no PHI visibility
    redacted = api.get(url)
    assert redacted.status_code == 200
    assert redacted.data["first_name"] == "***"

    api.force_authenticate(user=crc)  # CRC: PHI visible (minimum-necessary)
    visible = api.get(url)
    assert visible.data["first_name"] == "Jane"
