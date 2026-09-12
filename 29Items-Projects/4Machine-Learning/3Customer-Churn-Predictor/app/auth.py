"""Lightweight shared-password gate for the Streamlit app.

A no-op when ``APP_PASSWORD`` is unset (the default), so local/dev and tests are
unaffected. When set, every page calls :func:`require_auth` at the top to gate
access. For production, prefer Streamlit Cloud SSO / a reverse-proxy OAuth layer
(see ARCHITECTURE.md §2.5).
"""

from __future__ import annotations

import hmac
import os

import streamlit as st


def _configured_password() -> str | None:
    pw = os.environ.get("APP_PASSWORD")
    if pw:
        return pw
    try:  # Streamlit secrets are optional and may not exist locally.
        return st.secrets.get("APP_PASSWORD")  # type: ignore[no-any-return]
    except Exception:  # noqa: BLE001
        return None


def require_auth() -> None:
    """Stop rendering the page unless the user is authenticated."""
    password = _configured_password()
    if not password:  # auth disabled
        return
    if st.session_state.get("_authenticated"):
        return

    entered = st.text_input("🔒 Enter app password", type="password")
    if not entered:
        st.stop()
    if hmac.compare_digest(entered, password):
        st.session_state["_authenticated"] = True
        st.rerun()
    else:
        st.error("Incorrect password.")
        st.stop()
