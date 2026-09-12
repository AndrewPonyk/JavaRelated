"""Audit signal receivers.

Domain models opt into automatic auditing by listing their label in
``AUDITED_MODELS``. Receivers run inside the same transaction as the change, so a
clinical mutation and its audit row commit atomically — the trail can never be
silently lost.

Field-level before/after diffs are captured by snapshotting the instance in
``pre_save`` and diffing in ``post_save``. **PHI is never written to the audit
trail**: encrypted fields are recorded as changed but their values are redacted.
"""
from __future__ import annotations

import datetime
import decimal
import uuid

from django.db.models.signals import post_delete, post_save, pre_save
from django.dispatch import receiver

from apps.common.fields import EncryptedCharField

from .middleware import get_audit_context
from .models import AuditAction, AuditEvent

# Only these models are auto-audited (avoid noise from infra tables).
AUDITED_MODELS = {
    "trials.Study",
    "trials.Protocol",
    "trials.EligibilityCriterion",
    "patients.Subject",
    "enrollment.Enrollment",
    "ecrf.DataPoint",
    "eligibility.Screening",
}

# auto-managed / noisy fields excluded from diffs.
_SKIP_FIELDS = {"updated_at", "created_at", "public_id"}


def _label(instance) -> str:
    return f"{instance._meta.app_label}.{instance.__class__.__name__}"


def _json_safe(value):
    if isinstance(value, datetime.date | datetime.datetime | uuid.UUID | decimal.Decimal):
        return str(value)
    return value


@receiver(pre_save)
def snapshot_old(sender, instance, **kwargs) -> None:
    if _label(instance) not in AUDITED_MODELS or instance.pk is None:
        return
    try:
        old = sender.objects.get(pk=instance.pk)
    except sender.DoesNotExist:
        return
    instance._audit_old = {f.attname: getattr(old, f.attname) for f in sender._meta.concrete_fields}


def _diff(instance) -> dict:
    old = getattr(instance, "_audit_old", None)
    if old is None:
        return {}
    changes: dict[str, list] = {}
    phi_fields = {
        f.attname for f in instance._meta.concrete_fields if isinstance(f, EncryptedCharField)
    }
    for f in instance._meta.concrete_fields:
        if f.name in _SKIP_FIELDS:
            continue
        new_val = getattr(instance, f.attname)
        old_val = old.get(f.attname)
        if old_val == new_val:
            continue
        if f.attname in phi_fields:
            changes[f.name] = ["***", "***"]  # PHI redacted from the trail
        else:
            changes[f.name] = [_json_safe(old_val), _json_safe(new_val)]
    return changes


def _write_event(instance, action: str, changes: dict | None = None) -> None:
    ctx = get_audit_context()
    last = AuditEvent.objects.order_by("-id").values_list("row_hash", flat=True).first()
    AuditEvent.objects.create(
        actor_id=ctx.actor_id,
        actor_label=ctx.actor_label or "system",
        action=action,
        correlation_id=ctx.correlation_id,
        source_ip=ctx.source_ip,
        entity_type=_label(instance),
        entity_id=str(getattr(instance, "pk", "")),
        changes=changes or {},
        prev_hash=last or "",
    )


@receiver(post_save)
def audit_save(sender, instance, created, **kwargs) -> None:
    if _label(instance) not in AUDITED_MODELS:
        return
    if created:
        _write_event(instance, AuditAction.CREATE, changes={})
    else:
        _write_event(instance, AuditAction.UPDATE, changes=_diff(instance))


@receiver(post_delete)
def audit_delete(sender, instance, **kwargs) -> None:
    if _label(instance) not in AUDITED_MODELS:
        return
    _write_event(instance, AuditAction.DELETE)
