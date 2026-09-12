"""Headless end-to-end tests via Streamlit's AppTest — no browser, no server.

Covers: the full app boots, every page renders in demo mode, and the main
analysis flows work through real widget interactions (select → submit → result).
"""

from __future__ import annotations

import pytest

pytest.importorskip("streamlit", reason="streamlit not installed")
from streamlit.testing.v1 import AppTest  # noqa: E402

pytestmark = pytest.mark.e2e


_PAGES = (
    "home",
    "data_explorer",
    "hypothesis_testing",
    "regression",
    "distribution_fitting",
    "ab_testing",
    "experiments",
)


def _page_runner(module_name: str) -> None:
    """Standalone script body for AppTest.from_function — the function's *source*
    is executed as a fresh script, so everything it needs is imported inside."""
    import importlib

    importlib.import_module(module_name).render()


def _run_page(page_name: str) -> AppTest:
    at = AppTest.from_function(
        _page_runner, args=(f"app.ui.pages.{page_name}",), default_timeout=60
    )
    at.run()
    return at


def _assert_clean(at: AppTest) -> None:
    assert not at.exception, f"page raised: {at.exception}"
    assert not at.error, f"page showed errors: {[e.value for e in at.error]}"


def test_full_app_boots_without_exception() -> None:
    at = AppTest.from_file("streamlit_app.py", default_timeout=60)
    at.run()
    assert not at.exception


def test_home_page_renders_title() -> None:
    at = AppTest.from_file("streamlit_app.py", default_timeout=60)
    at.run()
    assert any("Statistics Dashboard" in t.value for t in at.title)


@pytest.mark.parametrize("name", _PAGES)
def test_every_page_renders_in_demo_mode(name: str) -> None:
    _assert_clean(_run_page(name))


def test_hypothesis_flow_continuous_outcome() -> None:
    # Widget auto-IDs derive from construction params: when one selection changes
    # another widget's options, values must be applied one run at a time.
    at = _run_page("hypothesis_testing")
    # selectboxes: [0] demo dataset, [1] outcome, [2] group, [3] alpha
    at.selectbox[1].select("session_minutes")
    at.run()
    at.selectbox[2].select("variant")
    at.run()
    at.button[0].click()
    at.run()
    _assert_clean(at)
    assert any(m.label == "p-value" for m in at.metric)
    assert at.get("arrow_data_frame") or at.dataframe is not None  # result surfaces render
    assert at.get("download_button"), "expected the HTML report download"


def test_hypothesis_flow_binary_outcome_uses_proportions() -> None:
    at = _run_page("hypothesis_testing")
    at.selectbox[1].select("converted")
    at.run()
    at.selectbox[2].select("variant")
    at.run()
    at.button[0].click()
    at.run()
    _assert_clean(at)
    assert any(m.label == "p-value" for m in at.metric)


def test_regression_flow_fits_ols() -> None:
    at = _run_page("regression")
    # selectboxes: [0] demo dataset, [1] outcome; multiselect: [0] features
    at.selectbox[1].select("session_minutes")
    at.run()
    at.multiselect[0].select("converted")
    at.run()
    at.button[0].click()
    at.run()
    _assert_clean(at)
    assert any(m.label == "Observations" for m in at.metric)


def test_distribution_flow_ranks_candidates() -> None:
    at = _run_page("distribution_fitting")
    at.selectbox[1].select("session_minutes")
    at.run()
    at.button[0].click()
    at.run()
    _assert_clean(at)
    assert len(at.dataframe) >= 1  # the AIC ranking table


def test_ab_flow_analyzes_conversion() -> None:
    at = _run_page("ab_testing")
    # selectboxes: [0] demo dataset, [1] variant col, [2] metric, [3] alpha, then plan-tab widgets
    at.selectbox[1].select("variant")
    at.run()
    at.selectbox[2].select("converted")
    at.run()
    at.button[0].click()
    at.run()
    _assert_clean(at)
    assert any(m.label == "p-value" for m in at.metric)
    assert any("beats" in m.label for m in at.metric)  # Bayesian companion rendered


def test_ab_plan_tab_computes_sample_size() -> None:
    at = _run_page("ab_testing")
    _assert_clean(at)
    assert any(m.label == "Per variant" for m in at.metric)


def test_explorer_kind_override_persists_in_session() -> None:
    at = _run_page("data_explorer")
    _assert_clean(at)
    override_boxes = [sb for sb in at.selectbox if sb.key and sb.key.startswith("kind_override_")]
    assert override_boxes, "expected per-column override selectboxes"
    target = next(sb for sb in override_boxes if sb.key == "kind_override_converted")
    target.select("ordinal")
    at.run()
    _assert_clean(at)
    assert at.session_state["column_kind_overrides"] == {"converted": "ordinal"}
