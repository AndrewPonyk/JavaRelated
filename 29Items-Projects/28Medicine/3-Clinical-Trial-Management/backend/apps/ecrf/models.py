"""Electronic Case Report Forms (eCRF) — structured clinical data capture.

A ``FormDefinition`` is the template (fields + edit checks). A ``DataPoint`` is a
single captured value; every value carries its own audit trail and can have data
``Query`` rows raised against it by the data manager.
"""
from __future__ import annotations

from django.db import models

from apps.common.models import BaseModel


class FormDefinition(BaseModel):
    study = models.ForeignKey("trials.Study", on_delete=models.CASCADE, related_name="forms")
    name = models.CharField(max_length=255)  # e.g. "Vitals", "Adverse Event"
    # Field schema + edit-check rules (JSON-schema-like).
    schema = models.JSONField(default=dict, blank=True)

    def __str__(self) -> str:
        return self.name


class DataPoint(BaseModel):
    enrollment = models.ForeignKey(
        "enrollment.Enrollment", on_delete=models.PROTECT, related_name="data_points"
    )
    form = models.ForeignKey(FormDefinition, on_delete=models.PROTECT, related_name="data_points")
    field_name = models.CharField(max_length=128)
    value = models.JSONField(null=True, blank=True)  # scalar or structured
    # Source data verification flag for monitors.
    sdv_verified = models.BooleanField(default=False)

    class Meta:
        indexes = [models.Index(fields=["enrollment", "form", "field_name"])]


class QueryStatus(models.TextChoices):
    OPEN = "OPEN", "Open"
    ANSWERED = "ANSWERED", "Answered"
    CLOSED = "CLOSED", "Closed"


class Query(BaseModel):
    """A data-quality query raised against a DataPoint (CDM workflow)."""

    data_point = models.ForeignKey(DataPoint, on_delete=models.CASCADE, related_name="queries")
    status = models.CharField(max_length=10, choices=QueryStatus.choices, default=QueryStatus.OPEN)
    message = models.TextField()

    class Meta:
        verbose_name_plural = "queries"


# TODO: export task (export_dataset_*) producing SAS XPT / CSV for biostatistics,
# routed to the 'reports' Celery queue.
