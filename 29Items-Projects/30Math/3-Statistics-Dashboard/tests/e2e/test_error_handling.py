"""guard_page behavior under a real Streamlit runtime (AppTest).

The contract (docs/ARCHITECTURE.md §2.6): expected errors render their
user_message; unexpected errors render a generic apology and never leak
internals; Streamlit's own control-flow exceptions pass through untouched.
"""

from __future__ import annotations

import pytest

pytest.importorskip("streamlit", reason="streamlit not installed")
from streamlit.testing.v1 import AppTest  # noqa: E402

pytestmark = pytest.mark.e2e


def _script_app_error() -> None:
    from app.core.errors import AnalysisError, guard_page

    @guard_page
    def page() -> None:
        raise AnalysisError("internal detail", user_message="Friendly explanation.")

    page()


def _script_unexpected_error() -> None:
    from app.core.errors import guard_page

    @guard_page
    def page() -> None:
        raise RuntimeError("secret internal detail")

    page()


def _script_control_flow() -> None:
    import streamlit as st

    from app.core.errors import guard_page

    @guard_page
    def page() -> None:
        st.markdown("rendered before stop")
        st.stop()

    page()


def test_app_error_shows_user_message_not_a_crash() -> None:
    at = AppTest.from_function(_script_app_error)
    at.run()
    assert not at.exception
    assert any("Friendly explanation." in e.value for e in at.error)


def test_unexpected_error_shows_generic_apology_and_hides_details() -> None:
    at = AppTest.from_function(_script_unexpected_error)
    at.run()
    assert not at.exception
    assert at.error, "expected a generic error banner"
    assert all("secret internal detail" not in e.value for e in at.error)
    assert any("logged" in e.value for e in at.error)


def test_streamlit_control_flow_passes_through_the_guard() -> None:
    at = AppTest.from_function(_script_control_flow)
    at.run()
    assert not at.exception
    assert not at.error  # st.stop() is navigation, not an error
    assert any("rendered before stop" in m.value for m in at.markdown)
