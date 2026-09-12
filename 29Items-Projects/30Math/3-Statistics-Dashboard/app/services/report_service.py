"""Self-contained HTML reports for analysis results.

Everything user-controlled is HTML-escaped; the document has zero external
assets (inline CSS only) so it renders anywhere it is emailed or archived.
"""

from __future__ import annotations

import html
from datetime import UTC, datetime

from app.services.analysis_service import AnalysisBundle

_STYLE = """
body { font-family: system-ui, -apple-system, "Segoe UI", sans-serif; color: #0b0b0b;
       background: #fcfcfb; max-width: 46rem; margin: 2rem auto; padding: 0 1rem; }
h1 { font-size: 1.4rem; } h2 { font-size: 1.05rem; margin-top: 1.6rem; }
table { border-collapse: collapse; width: 100%; font-size: 0.9rem; }
th, td { border-bottom: 1px solid #e1e0d9; padding: 0.4rem 0.6rem; text-align: left; }
.verdict { font-weight: 600; }
.warn { background: #fff7e6; border-left: 3px solid #eda100; padding: 0.5rem 0.8rem; margin: 0.4rem 0; }
.muted { color: #898781; font-size: 0.85rem; }
"""


def report_filename(bundle: AnalysisBundle) -> str:
    stamp = datetime.now(tz=UTC).strftime("%Y%m%d-%H%M%S")
    return f"analysis-{bundle.result.test.value}-{stamp}.html"


def render_html_report(bundle: AnalysisBundle, dataset_name: str) -> str:
    result, recommendation, profile = bundle.result, bundle.recommendation, bundle.profile
    esc = html.escape

    verdict = (
        f"Statistically significant at α = {result.alpha:g}"
        if result.significant
        else f"Not statistically significant at α = {result.alpha:g}"
    )
    effect_row = ""
    if result.effect_size is not None:
        effect_row = (
            f"<tr><th>{esc(result.effect_size.name)}</th>"
            f"<td>{result.effect_size.value:.4f} ({esc(result.effect_size.magnitude)})</td></tr>"
        )

    reasons = "".join(f"<li>{esc(reason)}</li>" for reason in recommendation.reasons)
    warnings = "".join(
        f'<div class="warn">{esc(warning)}</div>'
        for warning in (*recommendation.warnings, *result.warnings)
    )

    posthoc_section = ""
    if bundle.posthoc:
        rows = "".join(
            f"<tr><td>{esc(p.group_a)} vs {esc(p.group_b)}</td>"
            f"<td>{p.statistic:.4g}</td><td>{p.p_raw:.4g}</td><td>{p.p_adjusted:.4g}</td>"
            f"<td>{'yes' if p.significant else 'no'}</td></tr>"
            for p in bundle.posthoc
        )
        posthoc_section = (
            "<h2>Pairwise follow-ups (Holm-corrected)</h2>"
            "<table><tr><th>pair</th><th>statistic</th><th>p (raw)</th>"
            f"<th>p (adjusted)</th><th>significant</th></tr>{rows}</table>"
        )

    generated = datetime.now(tz=UTC).strftime("%Y-%m-%d %H:%M UTC")
    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<title>{esc(result.label)} — {esc(dataset_name)}</title>
<style>{_STYLE}</style></head>
<body>
<h1>{esc(result.label)}</h1>
<p class="muted">Dataset: {esc(dataset_name)} · outcome <code>{esc(profile.outcome)}</code>
 by <code>{esc(profile.group)}</code> · generated {generated} by Statistics Dashboard</p>

<p class="verdict">{esc(verdict)}</p>
<table>
<tr><th>p-value</th><td>{result.p_value:.6g}</td></tr>
<tr><th>Test statistic</th><td>{result.statistic:.6g}</td></tr>
{effect_row}
<tr><th>Groups</th><td>{esc(", ".join(f"{k} (n={v})" for k, v in profile.group_sizes.items()))}</td></tr>
</table>

<h2>Interpretation</h2>
<p>{esc(result.interpretation)}</p>

<h2>Why this test was selected</h2>
<ul>{reasons}</ul>

{posthoc_section}

{"<h2>Caveats</h2>" + warnings if warnings else ""}

<p class="muted">Computed in {bundle.duration_ms} ms. Effect sizes accompany every p-value;
assumption checks are part of the automated selection above.</p>
</body></html>
"""
