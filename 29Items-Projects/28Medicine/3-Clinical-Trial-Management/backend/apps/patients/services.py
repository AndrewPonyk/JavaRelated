"""Patient services: subject code generation + de-identification.

``deidentify`` produces the HIPAA Safe-Harbor-style view passed to the ML
pipeline — it must never contain a direct identifier.
"""
from __future__ import annotations

from django.db import transaction
from django.db.models import Max
from django.utils.crypto import get_random_string

from .models import Subject


def generate_subject_code(prefix: str = "S") -> str:
    """Generate a unique, non-identifying subject code, e.g. 'S-000123-AB'."""
    last = Subject.objects.aggregate(n=Max("id")).get("n") or 0
    suffix = get_random_string(2, allowed_chars="ABCDEFGHJKLMNPQRSTUVWXYZ")
    return f"{prefix}-{last + 1:06d}-{suffix}"


@transaction.atomic
def create_subject(*, created_by=None, **phi) -> Subject:
    code = phi.pop("subject_code", None) or generate_subject_code()
    return Subject.objects.create(subject_code=code, created_by=created_by, **phi)


def deidentify(subject: Subject) -> dict:
    """Return a PHI-free representation for ML / analytics use."""
    return {
        "subject_code": subject.subject_code,
        "age": subject.age,
        "sex_at_birth": subject.sex_at_birth,
    }
