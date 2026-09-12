"""Notification tasks (dedicated ``notifications`` queue).

Fired from domain events and Beat schedules; never inline in a request path.
All sends are idempotent via the notification ``dedupe_key``.
"""
from __future__ import annotations

import datetime
import logging

from celery import shared_task
from django.utils import timezone

logger = logging.getLogger(__name__)

REMINDER_HORIZON_DAYS = 3


@shared_task(name="apps.notifications.tasks.send_visit_reminders")
def send_visit_reminders() -> dict:
    """Daily: remind enrollment owners of upcoming (<=3d) and overdue visits."""
    from apps.enrollment.models import Visit, VisitStatus

    from .services import notify_visit

    today = timezone.now().date()
    horizon = today + datetime.timedelta(days=REMINDER_HORIZON_DAYS)
    sent = 0
    pending = Visit.objects.filter(
        status=VisitStatus.SCHEDULED, actual_date__isnull=True
    ).select_related("enrollment__subject", "enrollment__created_by", "template")
    for visit in pending.iterator():
        if visit.scheduled_date < today:
            sent += int(bool(notify_visit(visit, overdue=True)))
        elif visit.scheduled_date <= horizon:
            sent += int(bool(notify_visit(visit, overdue=False)))
    logger.info("Visit reminders dispatched: %s", sent)
    return {"sent": sent}


@shared_task(name="apps.notifications.tasks.notify_deviation")
def notify_deviation(deviation_id: int) -> dict:
    """Notify the enrollment owner of a protocol deviation. Idempotent."""
    from apps.enrollment.models import Deviation

    from .models import NotificationKind
    from .services import dispatch

    deviation = (
        Deviation.objects.select_related(
            "enrollment__created_by", "enrollment__subject", "template"
        )
        .filter(pk=deviation_id)
        .first()
    )
    if deviation is None or deviation.enrollment.created_by is None:
        return {"notified": False}
    created = dispatch(
        recipient=deviation.enrollment.created_by,
        kind=NotificationKind.DEVIATION,
        subject=f"Protocol deviation: {deviation.get_kind_display()}",
        body=deviation.detail,
        dedupe_key=f"deviation:{deviation.pk}",
        entity_type="enrollment.Deviation",
        entity_id=deviation.pk,
    )
    return {"notified": bool(created)}
