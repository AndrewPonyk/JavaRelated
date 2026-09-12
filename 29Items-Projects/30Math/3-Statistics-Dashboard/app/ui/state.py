"""Typed accessors for st.session_state.

Session state is per-browser-tab and lives in process memory — it is lost on
redeploy/restart; anything durable belongs in PostgreSQL (TECH-NOTES §3.6.4).

Pages store their last computed result via remember()/recall() so that widget
interactions after an analysis (downloads, toggles) don't wipe the output on
Streamlit's rerun (TECH-NOTES §3.6.1).
"""

from __future__ import annotations

from typing import Any

import pandas as pd
import streamlit as st

from app.stats.profiler import VarKind

ACTIVE_DF = "active_df"
ACTIVE_DF_NAME = "active_df_name"
ACTIVE_DATASET_ID = "active_dataset_id"  # set when the active data is saved/loaded from the library
KIND_OVERRIDES = "column_kind_overrides"  # {column: VarKind.value} set in the Data Explorer


def set_active_df(df: pd.DataFrame, name: str, dataset_id: str | None = None) -> None:
    st.session_state[ACTIVE_DF] = df
    st.session_state[ACTIVE_DF_NAME] = name
    st.session_state[ACTIVE_DATASET_ID] = dataset_id


def get_active_df() -> pd.DataFrame | None:
    return st.session_state.get(ACTIVE_DF)


def get_active_df_name() -> str:
    return st.session_state.get(ACTIVE_DF_NAME, "")


def get_active_dataset_id() -> str | None:
    return st.session_state.get(ACTIVE_DATASET_ID)


def set_active_dataset_id(dataset_id: str | None) -> None:
    st.session_state[ACTIVE_DATASET_ID] = dataset_id


def get_kind_overrides() -> dict[str, VarKind]:
    raw: dict[str, str] = st.session_state.get(KIND_OVERRIDES, {})
    return {column: VarKind(value) for column, value in raw.items()}


def set_kind_override(column: str, kind: VarKind | None) -> None:
    """Assign (or clear, with None) an analyst override for a column's kind."""
    overrides: dict[str, str] = dict(st.session_state.get(KIND_OVERRIDES, {}))
    if kind is None:
        overrides.pop(column, None)
    else:
        overrides[column] = kind.value
    st.session_state[KIND_OVERRIDES] = overrides


def remember(key: str, value: Any) -> None:
    st.session_state[key] = value


def recall(key: str, default: Any = None) -> Any:
    return st.session_state.get(key, default)
