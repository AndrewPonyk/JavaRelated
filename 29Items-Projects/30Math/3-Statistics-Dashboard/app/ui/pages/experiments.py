"""Experiments registry: create, track, and close A/B experiments (full CRUD)."""

from __future__ import annotations

import streamlit as st

from app.core.config import get_settings
from app.core.errors import DataValidationError, guard_page
from app.services import experiment_service

_STATUS_BADGES = {"draft": "📝 draft", "running": "🟢 running", "completed": "🏁 completed"}


@guard_page
def render() -> None:
    st.title("⚗️ Experiments")
    st.caption(
        "Pre-register experiments (hypothesis, metric, planned sample size, traffic split). "
        "Analyses linked to an experiment inherit its split for the SRM check and warn on "
        "interim looks below the planned n."
    )

    if get_settings().demo_mode:
        st.info(
            "The experiment registry needs a database — configure DATABASE_URL "
            "(see .env.example) to enable it.",
            icon="ℹ️",
        )
        return

    _render_create_form()
    st.divider()
    _render_registry()


def _render_create_form() -> None:
    with st.form("create_experiment", clear_on_submit=True):
        st.subheader("New experiment")
        name = st.text_input("Name (unique)", max_chars=200)
        hypothesis = st.text_area(
            "Hypothesis", placeholder="Variant B's new checkout increases conversion.", height=80
        )
        col_metric, col_n, col_ratio = st.columns((2, 1, 1))
        metric = col_metric.text_input("Primary metric", placeholder="converted")
        planned_n = col_n.number_input("Planned n / variant", min_value=0, value=0, step=100)
        ratio = col_ratio.slider("Expected share of variant A", 0.1, 0.9, 0.5, step=0.05)
        submitted = st.form_submit_button("Create", type="primary")

    if not submitted:
        return
    if not name.strip():
        st.error("The experiment needs a name.", icon="⚠️")
        return
    try:
        experiment_service.create_experiment(
            name,
            hypothesis=hypothesis or None,
            primary_metric=metric or None,
            planned_n_per_variant=int(planned_n) or None,
            expected_ratio=float(ratio),
        )
        st.toast(f"Experiment '{name}' created.", icon="⚗️")
    except DataValidationError as exc:
        st.error(exc.user_message, icon="⚠️")


def _render_registry() -> None:
    experiments = experiment_service.list_experiments()
    if not experiments:
        st.info("No experiments yet — create the first one above.")
        return

    st.subheader(f"Registry ({len(experiments)})")
    for experiment in experiments:
        with st.container(border=True):
            head, badge = st.columns((4, 1))
            head.markdown(f"**{experiment.name}**")
            badge.markdown(_STATUS_BADGES.get(experiment.status, experiment.status))
            if experiment.hypothesis:
                st.markdown(f"*{experiment.hypothesis}*")
            st.caption(
                f"metric: {experiment.primary_metric or '—'} · planned n/variant: "
                f"{experiment.planned_n_per_variant or '—'} · split A: "
                f"{experiment.expected_ratio:.0%} · logged runs: {experiment.run_count} · "
                f"created {experiment.created_at}"
            )
            _render_actions(experiment)


def _render_actions(experiment: experiment_service.ExperimentInfo) -> None:
    col_advance, col_delete, _ = st.columns((1, 2, 2))
    if experiment.status == "draft" and col_advance.button("▶ Start", key=f"start_{experiment.id}"):
        experiment_service.advance_status(experiment.id, "running")
        st.rerun()
    if experiment.status == "running" and col_advance.button(
        "🏁 Complete", key=f"complete_{experiment.id}"
    ):
        experiment_service.advance_status(experiment.id, "completed")
        st.rerun()
    with col_delete:
        confirm = st.checkbox("confirm", key=f"confirm_del_{experiment.id}")
        if st.button("🗑️ Delete", key=f"delete_{experiment.id}", disabled=not confirm):
            experiment_service.delete_experiment(experiment.id)
            st.rerun()
