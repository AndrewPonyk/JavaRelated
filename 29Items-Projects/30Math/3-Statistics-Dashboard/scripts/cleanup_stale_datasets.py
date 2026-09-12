"""Data retention: delete saved datasets with no analysis runs after N days.

Usage:
    python scripts/cleanup_stale_datasets.py             # dry run, 90-day cutoff
    python scripts/cleanup_stale_datasets.py --apply
    python scripts/cleanup_stale_datasets.py --days 30 --apply

Runs weekly in CI (.github/workflows/cleanup.yml) when a DATABASE_URL secret is
configured. Datasets that back at least one analysis run are always kept —
runs must stay reproducible against their data snapshot.
"""

from __future__ import annotations

import argparse
import sys
from datetime import UTC, datetime, timedelta
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parents[1]))

from sqlalchemy import select

from app.core.config import get_settings
from app.core.logging import setup_logging
from app.data.db import dispose_engine, session_scope
from app.data.models import AnalysisRun, Dataset


def find_stale(days: int) -> list[tuple[str, str]]:
    """(id, name) of datasets older than the cutoff that no run references."""
    cutoff = datetime.now(tz=UTC) - timedelta(days=days)
    with session_scope() as session:
        referenced = select(AnalysisRun.dataset_id).distinct().scalar_subquery()
        stmt = select(Dataset.id, Dataset.name).where(
            Dataset.created_at < cutoff, Dataset.id.not_in(referenced)
        )
        return [(row[0], row[1]) for row in session.execute(stmt)]


def delete_datasets(ids: list[str]) -> int:
    from app.data.repositories import DatasetRepository

    deleted = 0
    with session_scope() as session:
        repo = DatasetRepository(session)
        for dataset_id in ids:
            deleted += int(repo.delete(dataset_id))
    return deleted


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--days", type=int, default=90, help="age cutoff in days (default 90)")
    parser.add_argument("--apply", action="store_true", help="actually delete (default: dry run)")
    args = parser.parse_args()

    setup_logging()
    if get_settings().demo_mode:
        print("No DATABASE_URL configured — nothing to clean.")
        return 0

    stale = find_stale(args.days)
    if not stale:
        print(f"No unreferenced datasets older than {args.days} days.")
        return 0

    for dataset_id, name in stale:
        print(f"stale: {name} ({dataset_id})")
    if not args.apply:
        print(f"Dry run: {len(stale)} dataset(s) would be deleted. Re-run with --apply.")
        return 0

    deleted = delete_datasets([dataset_id for dataset_id, _ in stale])
    print(f"Deleted {deleted} dataset(s).")
    dispose_engine()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
