"""Build graded relevance judgments from logged search interactions.

Input : search_events (PostgreSQL) — impressions carry shown_product_ids,
        clicks carry clicked_product_id + clicked_position.
Output: judgments.tsv — `grade<TAB>qid:N<TAB>keywords<TAB>product_id` for train.py.

Grading uses COEC (Clicks Over Expected Clicks) position debiasing: a click at
position 9 is worth more than a click at position 0, because position 0 gets
clicked regardless of relevance.

Usage: python ml/ltr/build_judgments.py --days 30 --min-impressions 5
"""

import argparse
import asyncio
import sys
from collections import defaultdict
from datetime import UTC, datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "backend"))

SMOOTHING_CLICKS = 0.1
SMOOTHING_IMPRESSIONS = 1.0


def compute_position_baseline(
    impressions_by_position: dict[int, int], clicks_by_position: dict[int, int]
) -> dict[int, float]:
    """Smoothed global CTR per rank position — the 'expected clicks' denominator."""
    positions = set(impressions_by_position) | set(clicks_by_position)
    return {
        pos: (clicks_by_position.get(pos, 0) + SMOOTHING_CLICKS)
        / (impressions_by_position.get(pos, 0) + SMOOTHING_IMPRESSIONS)
        for pos in positions
    }


def grade_from_coec(clicks: int, expected_clicks: float) -> int:
    """COEC score → 0-3 relevance grade."""
    if clicks == 0:
        return 0
    score = clicks / max(expected_clicks, 1e-6)
    if score >= 2.0:
        return 3
    if score >= 1.0:
        return 2
    return 1


async def build_judgments(days: int, min_impressions: int, output_path: str) -> None:
    from app.db.models.search_event import SearchEvent
    from app.db.session import async_session_factory
    from sqlalchemy import select

    cutoff = datetime.now(UTC) - timedelta(days=days)

    impressions: dict[tuple[str, str], list[int]] = defaultdict(list)  # (q, pid) -> positions
    clicks: dict[tuple[str, str], int] = defaultdict(int)
    baseline_imps: dict[int, int] = defaultdict(int)
    baseline_clicks: dict[int, int] = defaultdict(int)

    async with async_session_factory() as session:
        events = await session.scalars(
            select(SearchEvent).where(SearchEvent.created_at >= cutoff)
        )
        for event in events:
            query = event.normalized_query
            if event.shown_product_ids:  # impression event
                for position, product_id in enumerate(event.shown_product_ids):
                    impressions[(query, str(product_id))].append(position)
                    baseline_imps[position] += 1
            if event.clicked_product_id:  # click event
                clicks[(query, str(event.clicked_product_id))] += 1
                baseline_clicks[event.clicked_position or 0] += 1

    if not impressions:
        sys.exit("No impression events in the window — nothing to grade.")

    baseline_ctr = compute_position_baseline(baseline_imps, baseline_clicks)

    qids: dict[str, int] = {}
    lines: list[str] = []
    kept = skipped = 0
    for (query, product_id), positions in sorted(impressions.items()):
        if len(positions) < min_impressions:
            skipped += 1
            continue
        expected = sum(baseline_ctr.get(pos, SMOOTHING_CLICKS) for pos in positions)
        grade = grade_from_coec(clicks.get((query, product_id), 0), expected)
        qid = qids.setdefault(query, len(qids) + 1)
        lines.append(f"{grade}\tqid:{qid}\t{query}\t{product_id}")
        kept += 1

    Path(output_path).write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(
        f"Wrote {kept} judgments for {len(qids)} queries to {output_path} "
        f"(skipped {skipped} low-traffic pairs, window {days}d)"
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--days", type=int, default=30)
    parser.add_argument("--min-impressions", type=int, default=5)
    parser.add_argument("--output", default="judgments.tsv")
    args = parser.parse_args()
    asyncio.run(build_judgments(args.days, args.min_impressions, args.output))
