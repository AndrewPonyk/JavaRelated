"""Fast tests for the shared CLI argument helpers."""

from __future__ import annotations

import argparse

import pytest

from lakehouse.common.cli import run_date_arg


def test_valid_iso_date_passes_through():
    assert run_date_arg("2026-07-03") == "2026-07-03"


@pytest.mark.parametrize("value", ["2026-7-3x", "03-07-2026", "yesterday", "2026-13-01", ""])
def test_malformed_dates_fail_argument_parsing(value):
    with pytest.raises(argparse.ArgumentTypeError, match="YYYY-MM-DD"):
        run_date_arg(value)


def test_helper_is_wired_into_a_job_parser():
    from lakehouse.jobs import bronze_to_silver  # noqa: F401 — importable without Spark running

    parser = argparse.ArgumentParser()
    parser.add_argument("--run-date", type=run_date_arg)
    with pytest.raises(SystemExit):
        parser.parse_args(["--run-date", "not-a-date"])
