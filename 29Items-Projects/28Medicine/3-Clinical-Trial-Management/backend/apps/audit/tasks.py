"""Audit integrity verification (scheduled nightly by Celery Beat).

Walks the hash chain and reports any row whose stored ``row_hash`` does not match
a recomputation, or whose ``prev_hash`` does not match the preceding row. A
mismatch indicates the append-only table was tampered with out-of-band and must
raise a high-severity alert.
"""
from __future__ import annotations

import logging

from celery import shared_task

from .models import AuditEvent

logger = logging.getLogger(__name__)


@shared_task(name="apps.audit.tasks.verify_audit_integrity")
def verify_audit_integrity(batch_size: int = 5000) -> dict:
    broken: list[int] = []
    prev_hash = ""
    qs = AuditEvent.objects.order_by("id").iterator(chunk_size=batch_size)
    for event in qs:
        expected = event.compute_hash()
        if event.row_hash != expected or event.prev_hash != prev_hash:
            broken.append(event.pk)
        prev_hash = event.row_hash

    result = {"checked_through": prev_hash[:12], "broken_count": len(broken)}
    if broken:
        # TODO: page on-call / raise to Sentry — this is a compliance incident.
        logger.error("Audit integrity violation detected for ids=%s", broken[:50])
    return result
