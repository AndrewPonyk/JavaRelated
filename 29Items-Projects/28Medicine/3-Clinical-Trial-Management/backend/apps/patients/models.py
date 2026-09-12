"""Subject (trial participant) model — PHI-bearing, encrypted at the field level.

HIPAA: direct identifiers are encrypted at rest (see common.fields.EncryptedCharField)
so PHI is protected even in a database dump. The ``subject_code`` is the
de-identified key used everywhere else (eCRF, eligibility, exports) — PHI is read
only where strictly necessary (minimum-necessary principle).
"""
from __future__ import annotations

from datetime import date

from django.db import models

from apps.common.fields import EncryptedCharField
from apps.common.models import BaseModel


class Subject(BaseModel):
    # De-identified study key — safe to use across the system and in exports.
    subject_code = models.CharField(max_length=32, unique=True, db_index=True)

    # ── Protected Health Information (encrypted at rest) ──────────────────
    first_name = EncryptedCharField(max_length=255, blank=True, default="")
    last_name = EncryptedCharField(max_length=255, blank=True, default="")
    medical_record_number = EncryptedCharField(max_length=128, blank=True, default="")
    date_of_birth = EncryptedCharField(max_length=32, blank=True, default="")  # ISO string

    # Non-identifying clinical context (safe to query/aggregate).
    sex_at_birth = models.CharField(max_length=16, blank=True, default="")
    enrolled = models.BooleanField(default=False)

    class Meta:
        verbose_name = "subject"
        ordering = ("subject_code",)

    def __str__(self) -> str:  # MUST stay PHI-free (used in logs/audit labels)
        return self.subject_code

    @property
    def age(self) -> int | None:
        """Age in years derived from the (decrypted) DOB, if present."""
        if not self.date_of_birth:
            return None
        try:
            dob = date.fromisoformat(self.date_of_birth[:10])
        except ValueError:
            return None
        today = date.today()
        return today.year - dob.year - ((today.month, today.day) < (dob.month, dob.day))
