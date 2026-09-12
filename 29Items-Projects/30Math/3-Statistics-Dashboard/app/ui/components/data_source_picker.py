"""Data source picker: demo datasets, validated CSV upload, and the saved library.

Stores the choice (plus its library id, when applicable) in session state.
Frames larger than ``max_analysis_rows`` are analyzed on a seeded sample with a
visible banner — transparent degradation instead of an OOM (TECH-NOTES §3.6.5).
"""

from __future__ import annotations

import pandas as pd
import streamlit as st

from app.core.config import get_settings
from app.core.errors import DataValidationError
from app.services import dataset_service
from app.ui import state

_SAMPLE_SEED = 42


@st.cache_data(show_spinner=False)
def _load_demo_cached(name: str) -> pd.DataFrame:
    return dataset_service.load_demo(name)


@st.cache_data(show_spinner="Loading saved dataset…")
def _load_saved_cached(dataset_id: str) -> tuple[pd.DataFrame, str]:
    return dataset_service.load_saved(dataset_id)


def _apply_sampling_cap(df: pd.DataFrame) -> pd.DataFrame:
    cap = get_settings().max_analysis_rows
    if len(df) <= cap:
        return df
    st.warning(
        f"Large dataset: analyzing a seeded random sample of {cap:,} of {len(df):,} rows "
        "(results are labeled accordingly).",
        icon="📉",
    )
    return df.sample(cap, random_state=_SAMPLE_SEED).reset_index(drop=True)


def render(*, key_prefix: str = "picker") -> pd.DataFrame | None:
    """Render the picker; returns the active DataFrame (also kept in session state)."""
    settings = get_settings()
    sources = ["Demo dataset", "Upload CSV"]
    if not settings.demo_mode:
        sources.append("Saved datasets")

    with st.container(border=True):
        source = st.radio("Data source", sources, horizontal=True, key=f"{key_prefix}_source")

        df: pd.DataFrame | None
        if source == "Demo dataset":
            df = _render_demo(key_prefix)
        elif source == "Upload CSV":
            df = _render_upload(key_prefix)
        else:
            df = _render_saved(key_prefix)

        if df is None:
            return state.get_active_df()

        st.caption(
            f"Active: **{state.get_active_df_name()}** — "
            f"{len(df):,} rows × {df.shape[1]} columns"
            + (" · 💾 in library" if state.get_active_dataset_id() else "")
        )
        return df


def _keep_library_link(display_name: str) -> str | None:
    """Preserve the saved-dataset id across reruns while the same source stays active.

    Without this, the rerun after "Save to library" would reset the linkage and
    analyses would silently stop being recorded against the dataset.
    """
    if state.get_active_df_name() == display_name:
        return state.get_active_dataset_id()
    return None


def _render_demo(key_prefix: str) -> pd.DataFrame | None:
    names = dataset_service.list_demo_datasets()
    if not names:
        st.info("No bundled demo datasets found — run `python scripts/seed_demo_data.py`.")
        return None
    name = st.selectbox("Demo dataset", names, key=f"{key_prefix}_demo")
    df = _apply_sampling_cap(_load_demo_cached(name).copy())  # never hand out the cached frame
    display_name = f"demo:{name}"
    state.set_active_df(df, display_name, dataset_id=_keep_library_link(display_name))
    _render_save_button(df, name, key_prefix, source_type="demo")
    return df


def _render_upload(key_prefix: str) -> pd.DataFrame | None:
    uploaded = st.file_uploader("CSV file", type=["csv"], key=f"{key_prefix}_upload")
    if uploaded is None:
        return None
    try:
        df = dataset_service.read_upload(uploaded)
    except DataValidationError as exc:
        st.error(exc.user_message, icon="⚠️")
        return None
    df = _apply_sampling_cap(df)
    display_name = f"upload:{uploaded.name}"
    state.set_active_df(df, display_name, dataset_id=_keep_library_link(display_name))
    _render_save_button(df, uploaded.name.removesuffix(".csv"), key_prefix, source_type="upload")
    return df


def _render_saved(key_prefix: str) -> pd.DataFrame | None:
    saved = dataset_service.list_saved()
    if not saved:
        st.info("The library is empty — save an uploaded or demo dataset first.")
        return None
    labels = {
        item.id: f"{item.name} · {item.row_count:,}×{item.column_count} · {item.created_at[:16]}"
        for item in saved
    }
    dataset_id = st.selectbox(
        "Saved dataset",
        list(labels.keys()),
        format_func=lambda k: labels[k],
        key=f"{key_prefix}_saved",
    )
    df, name = _load_saved_cached(dataset_id)
    df = _apply_sampling_cap(df.copy())
    state.set_active_df(df, f"library:{name}", dataset_id=dataset_id)

    confirm = st.checkbox("Confirm deletion", key=f"{key_prefix}_del_confirm")
    if st.button("🗑️ Delete from library", key=f"{key_prefix}_delete", disabled=not confirm):
        dataset_service.delete_saved(dataset_id)
        _load_saved_cached.clear()
        state.set_active_dataset_id(None)
        st.toast("Dataset deleted.", icon="🗑️")
        st.rerun()
    return df


def _render_save_button(df: pd.DataFrame, name: str, key_prefix: str, *, source_type: str) -> None:
    if get_settings().demo_mode:
        return
    if st.button(
        "💾 Save to library",
        key=f"{key_prefix}_save",
        help="Stores the frame exactly as analyzed (after any sampling cap); "
        "identical data is deduplicated.",
    ):
        dataset_id = dataset_service.save_dataset(df, name, source_type=source_type)
        state.set_active_dataset_id(dataset_id or None)
        st.toast(f"Saved '{name}' to the library — analyses on it will be recorded.", icon="💾")
