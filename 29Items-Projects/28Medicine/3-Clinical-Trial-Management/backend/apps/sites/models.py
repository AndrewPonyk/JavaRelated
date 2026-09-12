"""Investigative sites and delegation-of-authority.

A Site runs one or more studies under a Principal Investigator. RBAC scopes most
users to their delegated site(s) so a CRC only sees their own subjects. The
``DelegationLog`` records who is authorized to perform which study tasks (ICH-GCP
delegation-of-authority requirement).
"""
from __future__ import annotations

from django.db import models

from apps.common.models import BaseModel


class Site(BaseModel):
    name = models.CharField(max_length=255)
    institution = models.CharField(max_length=255, blank=True, default="")
    principal_investigator = models.ForeignKey(
        "accounts.User", null=True, blank=True, on_delete=models.PROTECT, related_name="led_sites"
    )
    studies = models.ManyToManyField("trials.Study", related_name="sites", blank=True)

    def __str__(self) -> str:
        return self.name


class DelegationLog(BaseModel):
    """Records that a user is delegated to perform a task at a site/study."""

    site = models.ForeignKey(Site, on_delete=models.CASCADE, related_name="delegations")
    user = models.ForeignKey("accounts.User", on_delete=models.PROTECT, related_name="delegations")
    task = models.CharField(max_length=255)  # e.g. "Obtain informed consent"
    granted_on = models.DateField()
    revoked_on = models.DateField(null=True, blank=True)

    class Meta:
        ordering = ("-granted_on",)

    @property
    def is_active(self) -> bool:
        return self.revoked_on is None

    def __str__(self) -> str:
        return f"{self.user_id} · {self.task} @ {self.site_id}"
