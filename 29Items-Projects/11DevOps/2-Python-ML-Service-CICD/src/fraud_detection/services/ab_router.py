"""Champion/challenger traffic routing with sticky, DB-backed assignments.

Assignment policy (in order):

1. An existing ``ab_assignments`` row for the account wins - assignments
   stay sticky even when the traffic split changes mid-experiment.
2. Otherwise a deterministic SHA-256 hash bucket of the account id decides,
   so routing stays stable across replicas even without the database.

Database failures never break routing: they are logged, counted via
``fraud_db_errors_total`` and the hash decides.
"""

from __future__ import annotations

import hashlib
from datetime import UTC, datetime
from typing import Literal

from sqlalchemy.orm import Session

from fraud_detection.core.logging import get_logger
from fraud_detection.db.models import ABAssignment
from fraud_detection.db.repositories import ABAssignmentRepository
from fraud_detection.monitoring.metrics import FRAUD_DB_ERRORS_TOTAL
from fraud_detection.services.ab_config import ABConfigStore

logger = get_logger(__name__)

Variant = Literal["champion", "challenger"]
_VARIANTS = ("champion", "challenger")


def hash_bucket(account_id: str) -> int:
    """Map an account id to a stable bucket in ``[0, 100)``."""
    return int(hashlib.sha256(account_id.encode("utf-8")).hexdigest(), 16) % 100


class ABRouter:
    """Assigns accounts to A/B variants (sticky DB lookup, then hash split)."""

    def __init__(self, config_store: ABConfigStore | None = None) -> None:
        self._config = config_store or ABConfigStore()

    def assign_variant(self, account_id: str, session: Session | None = None) -> Variant:
        """Return the A/B variant for an account.

        Args:
            account_id: Stable account identifier.
            session: Optional DB session for the sticky-assignment lookup.

        Returns:
            ``"champion"`` or ``"challenger"``.
        """
        enabled, traffic_split = self._config.get()
        if not enabled:
            return "champion"

        if session is not None:
            try:
                existing = ABAssignmentRepository(session).get_by_account(account_id)
                if existing is not None and existing.variant in _VARIANTS:
                    return existing.variant  # type: ignore[return-value]
            except Exception:  # noqa: BLE001 - policy: routing survives DB outages
                FRAUD_DB_ERRORS_TOTAL.inc()
                logger.warning("ab_assignment_lookup_failed", account_id=account_id)

        return "challenger" if hash_bucket(account_id) < traffic_split else "champion"

    def record_assignment(
        self, account_id: str, variant: Variant, model_version: str, session: Session
    ) -> None:
        """Persist a first-seen assignment (no-op when one already exists).

        Best-effort: failures are logged and counted, never raised.
        """
        try:
            with session.begin_nested():
                repo = ABAssignmentRepository(session)
                if repo.get_by_account(account_id) is None:
                    repo.add(
                        ABAssignment(
                            account_id=account_id,
                            variant=variant,
                            model_version=model_version,
                            assigned_at=datetime.now(UTC),
                        )
                    )
        except Exception:  # noqa: BLE001 - policy: persistence is best-effort
            # SAVEPOINT scope: only this write is rolled back; sibling rows
            # already flushed on the session (the prediction row) survive.
            FRAUD_DB_ERRORS_TOTAL.inc()
            logger.warning("ab_assignment_persist_failed", account_id=account_id)
