"""Identity, RBAC and electronic-signature support.

Identity is federated (OIDC/SAML to the enterprise IdP, MFA enforced there); this
model holds the application's authorization data (role, site scoping) and the
e-signature meaning vocabulary required by 21 CFR Part 11 §11.200.
"""
from __future__ import annotations

from django.contrib.auth.models import AbstractUser
from django.db import models


class Role(models.TextChoices):
    SPONSOR = "SPONSOR", "Sponsor / CRO"
    PI = "PI", "Principal Investigator"
    CRC = "CRC", "Clinical Research Coordinator"
    CDM = "CDM", "Clinical Data Manager"
    MONITOR = "MONITOR", "Monitor / CRA"
    AUDITOR = "AUDITOR", "Auditor (read-only)"


class User(AbstractUser):
    """Custom user. ``role`` drives RBAC; ``AUDITOR`` is read-only by policy."""

    role = models.CharField(max_length=16, choices=Role.choices, default=Role.CRC)
    # MFA is enforced at the IdP; this flag mirrors enrollment status for UX.
    mfa_enrolled = models.BooleanField(default=False)

    @property
    def is_read_only(self) -> bool:
        return self.role == Role.AUDITOR

    def __str__(self) -> str:  # used as actor_label in audit events
        return f"{self.get_full_name() or self.username} ({self.role})"


class ElectronicSignature(models.Model):
    """A 21 CFR Part 11 e-signature: who, when, and the *meaning* of signing.

    Cryptographically bound to the signed record (entity_type/entity_id) and only
    created after a re-authentication challenge in the signing flow.
    """

    signer = models.ForeignKey(User, on_delete=models.PROTECT, related_name="signatures")
    meaning = models.CharField(max_length=64)  # e.g. "Approved", "Reviewed", "Authored"
    entity_type = models.CharField(max_length=120)
    entity_id = models.CharField(max_length=64)
    signed_at = models.DateTimeField(auto_now_add=True)
    signature_hash = models.CharField(max_length=64)  # binds signer+record+time

    class Meta:
        indexes = [models.Index(fields=["entity_type", "entity_id"])]

    def __str__(self) -> str:
        return f"{self.meaning} by {self.signer} @ {self.signed_at:%Y-%m-%d}"
