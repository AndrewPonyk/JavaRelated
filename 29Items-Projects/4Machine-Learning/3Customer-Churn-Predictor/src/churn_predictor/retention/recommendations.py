"""Map model explanations (SHAP drivers) to concrete retention playbook actions.

This is the "actionable" layer that makes the tool valuable to non-technical
users: instead of "feature X = 0.3 SHAP", it says "offer a contract upgrade".
"""

from __future__ import annotations

import logging
from dataclasses import dataclass

from churn_predictor.models.explain import FeatureContribution

logger = logging.getLogger(__name__)


@dataclass
class RetentionAction:
    """A recommended action with priority and the driver that triggered it."""

    title: str
    detail: str
    priority: str  # high | medium | low
    triggered_by: str


# Maps a feature (or feature prefix) to a retention playbook entry.
# Only fires when the feature *increases* churn risk (positive SHAP).
_PLAYBOOK: dict[str, RetentionAction] = {
    "num_support_tickets": RetentionAction(
        title="Proactive support outreach",
        detail="High support volume signals frustration. Assign a CSM to resolve "
        "open issues and follow up within 48h.",
        priority="high",
        triggered_by="num_support_tickets",
    ),
    "tickets_per_month": RetentionAction(
        title="Service quality review",
        detail="Elevated ticket rate per month. Audit recent tickets for recurring "
        "root causes and offer a goodwill credit.",
        priority="high",
        triggered_by="tickets_per_month",
    ),
    "contract_type": RetentionAction(
        title="Offer longer-term contract incentive",
        detail="Month-to-month customers churn more. Offer a discount to move to an "
        "annual contract.",
        priority="medium",
        triggered_by="contract_type",
    ),
    "monthly_charges": RetentionAction(
        title="Pricing / plan-fit review",
        detail="High monthly charges relative to usage. Recommend a right-sized plan "
        "or loyalty discount.",
        priority="medium",
        triggered_by="monthly_charges",
    ),
    "tenure_months": RetentionAction(
        title="Early-life onboarding nudge",
        detail="Low tenure raises risk. Trigger onboarding milestones and a check-in "
        "from the success team.",
        priority="medium",
        triggered_by="tenure_months",
    ),
    "payment_method": RetentionAction(
        title="Payment friction reduction",
        detail="Payment method correlates with churn. Encourage auto-pay enrollment "
        "to reduce involuntary churn.",
        priority="low",
        triggered_by="payment_method",
    ),
}

_DEFAULT = RetentionAction(
    title="General retention check-in",
    detail="No strong specific driver. Schedule a routine satisfaction check-in.",
    priority="low",
    triggered_by="default",
)


def _match_action(feature: str) -> RetentionAction | None:
    """Match a (possibly one-hot-expanded) feature name to a playbook entry."""
    for key, action in _PLAYBOOK.items():
        if feature == key or feature.startswith(key):
            return action
    return None


def recommend(
    drivers: list[FeatureContribution],
    *,
    max_actions: int = 3,
) -> list[RetentionAction]:
    """Produce ranked, de-duplicated retention actions from SHAP drivers.

    Only drivers that *increase* churn risk produce actions.
    """
    actions: list[RetentionAction] = []
    seen: set[str] = set()

    for d in drivers:
        if d.direction != "increases":
            continue
        action = _match_action(d.feature)
        if action and action.title not in seen:
            actions.append(action)
            seen.add(action.title)
        if len(actions) >= max_actions:
            break

    if not actions:
        actions.append(_DEFAULT)

    logger.debug("Recommended %d actions", len(actions))
    return actions
