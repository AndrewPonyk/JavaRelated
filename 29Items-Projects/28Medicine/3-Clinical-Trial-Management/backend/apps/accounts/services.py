"""Electronic signature service (21 CFR Part 11 §11.200).

Signing requires re-authentication (the signer re-enters their password); the
signature records who/when/meaning and is hashed against the target record so it
cannot be transplanted onto a different record.
"""
from __future__ import annotations

import hashlib

from django.utils import timezone

from apps.audit.middleware import get_audit_context
from apps.audit.models import AuditAction, AuditEvent

from .models import ElectronicSignature, User


class SignatureError(Exception):
    """Raised when re-authentication for signing fails."""


def sign(
    *,
    signer: User,
    meaning: str,
    entity_type: str,
    entity_id: str | int,
    password: str | None = None,
) -> ElectronicSignature:
    """Create an electronic signature after verifying the signer's identity."""
    if password is not None and not signer.check_password(password):
        raise SignatureError("Re-authentication failed.")

    signed_at = timezone.now()
    material = f"{signer.pk}:{meaning}:{entity_type}:{entity_id}:{signed_at.isoformat()}"
    signature_hash = hashlib.sha256(material.encode("utf-8")).hexdigest()

    signature = ElectronicSignature.objects.create(
        signer=signer,
        meaning=meaning,
        entity_type=entity_type,
        entity_id=str(entity_id),
        signature_hash=signature_hash,
    )

    # The act of signing is itself an audited event.
    ctx = get_audit_context()
    last = AuditEvent.objects.order_by("-id").values_list("row_hash", flat=True).first()
    AuditEvent.objects.create(
        actor=signer,
        actor_label=str(signer),
        action=AuditAction.ESIGN,
        correlation_id=ctx.correlation_id,
        source_ip=ctx.source_ip,
        entity_type=entity_type,
        entity_id=str(entity_id),
        changes={"meaning": meaning},
        reason=f"Electronic signature: {meaning}",
        prev_hash=last or "",
    )
    return signature
