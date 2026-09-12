"""Shared argparse helpers for job entrypoints."""

from __future__ import annotations

import argparse
from datetime import date


def run_date_arg(value: str) -> str:
    """Validate a --run-date/--feature-date argument as strict YYYY-MM-DD.

    Jobs interpolate this value into partition predicates (`replaceWhere`,
    filters), so it must be a well-formed ISO date — a typo'd value should
    fail argument parsing with a clear message, not a Spark analysis error
    twenty minutes into the run.
    """
    try:
        parsed = date.fromisoformat(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError(f"expected YYYY-MM-DD, got {value!r}") from exc
    return parsed.isoformat()
