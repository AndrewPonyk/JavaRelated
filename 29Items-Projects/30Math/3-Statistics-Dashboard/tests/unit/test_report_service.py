"""HTML report: content, escaping, and self-containment."""

from __future__ import annotations

import html

import numpy as np
import pandas as pd

from app.services import analysis_service, report_service


def _bundle(rng: np.random.Generator, outcome: str = "y") -> analysis_service.AnalysisBundle:
    df = pd.DataFrame(
        {
            outcome: np.concatenate([rng.normal(0, 1, 80), rng.normal(1, 1, 80)]),
            "g": ["a"] * 80 + ["b"] * 80,
        }
    )
    return analysis_service.run_group_comparison(df, outcome, "g")


def test_report_contains_the_essentials(rng: np.random.Generator) -> None:
    bundle = _bundle(rng)
    html_doc = report_service.render_html_report(bundle, "demo-data")
    assert html_doc.startswith("<!doctype html>")
    assert html.escape(bundle.result.label) in html_doc  # apostrophes render escaped
    assert "p-value" in html_doc
    assert "Why this test was selected" in html_doc
    assert "demo-data" in html_doc


def test_report_escapes_hostile_names(rng: np.random.Generator) -> None:
    hostile = "<script>alert('x')</script>"
    html_doc = report_service.render_html_report(_bundle(rng), hostile)
    assert "<script>" not in html_doc
    assert "&lt;script&gt;" in html_doc


def test_report_is_self_contained(rng: np.random.Generator) -> None:
    html_doc = report_service.render_html_report(_bundle(rng), "d")
    assert "http://" not in html_doc and "https://" not in html_doc  # zero external assets
    assert "<style>" in html_doc


def test_filename_is_stamped(rng: np.random.Generator) -> None:
    name = report_service.report_filename(_bundle(rng))
    assert name.startswith("analysis-student_t-") and name.endswith(".html")
