"""Notification dispatch helpers (idempotent)."""
from __future__ import annotations

from .models import Notification, NotificationKind


def dispatch(
    *,
    recipient,
    kind: str,
    subject: str,
    dedupe_key: str,
    body: str = "",
    entity_type: str = "",
    entity_id: str = "",
) -> Notification | None:
    """Create a notification unless one with the same dedupe_key already exists.

    Returns the new Notification, or ``None`` if it was a duplicate.
    """
    obj, created = Notification.objects.get_or_create(
        dedupe_key=dedupe_key,
        defaults={
            "recipient": recipient,
            "kind": kind,
            "subject": subject,
            "body": body,
            "entity_type": entity_type,
            "entity_id": str(entity_id),
        },
    )
    # TODO(prod): also fan out to email/SES here for non-in-app channels.
    return obj if created else None


def notify_visit(visit, *, overdue: bool) -> Notification | None:
    """Notify the enrollment owner about an upcoming or overdue visit."""
    recipient = visit.enrollment.created_by
    if recipient is None:
        return None
    kind = NotificationKind.VISIT_OVERDUE if overdue else NotificationKind.VISIT_REMINDER
    code = visit.enrollment.subject.subject_code  # de-identified — never PHI
    when = "overdue" if overdue else f"due {visit.scheduled_date}"
    return dispatch(
        recipient=recipient,
        kind=kind,
        subject=f"Visit {when}: {visit.template.name} ({code})",
        dedupe_key=f"visit:{visit.pk}:{kind}:{visit.scheduled_date}",
        entity_type="enrollment.Visit",
        entity_id=visit.pk,
    )
