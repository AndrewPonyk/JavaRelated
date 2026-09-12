"""Unit tests for the retention recommendation mapping."""

from __future__ import annotations

from churn_predictor.models.explain import FeatureContribution
from churn_predictor.retention.recommendations import recommend


def _driver(feature: str, direction: str = "increases") -> FeatureContribution:
    return FeatureContribution(
        feature=feature,
        value=1,
        shap_value=0.5 if direction == "increases" else -0.5,
        direction=direction,
    )


def test_recommend_maps_known_driver() -> None:
    actions = recommend([_driver("num_support_tickets")])
    assert actions
    assert actions[0].triggered_by == "num_support_tickets"
    assert actions[0].priority == "high"


def test_recommend_ignores_protective_drivers() -> None:
    # A driver that *decreases* churn risk should not generate an action.
    actions = recommend([_driver("num_support_tickets", direction="decreases")])
    assert actions[0].triggered_by == "default"


def test_recommend_dedupes_and_caps() -> None:
    drivers = [
        _driver("contract_type_Month-to-month"),  # one-hot prefix match
        _driver("contract_type_One year"),  # same playbook entry
        _driver("monthly_charges"),
        _driver("tenure_months"),
    ]
    actions = recommend(drivers, max_actions=2)
    assert len(actions) <= 2
    titles = [a.title for a in actions]
    assert len(titles) == len(set(titles))  # no duplicates
