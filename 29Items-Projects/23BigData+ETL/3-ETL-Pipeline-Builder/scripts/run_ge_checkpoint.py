"""Run a Great Expectations checkpoint from the CLI (CI step / manual gate).

Exit code 0 = suite passed, 1 = validation failed, 2 = setup problem.
Airflow uses common/validation.py instead; this wrapper is for humans and CI.
"""

from __future__ import annotations

import argparse
import os
import sys


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", required=True, help="e.g. raw_orders_checkpoint")
    parser.add_argument(
        "--ge-root",
        default=os.environ.get("GE_ROOT_DIR", "./great_expectations"),
        help="Great Expectations project root",
    )
    args = parser.parse_args()

    try:
        import great_expectations as gx
    except ImportError:
        print("great-expectations not installed:  pip install 'great-expectations==0.18.*'")
        return 2

    context = gx.get_context(context_root_dir=args.ge_root)
    result = context.run_checkpoint(checkpoint_name=args.checkpoint)

    stats = getattr(result, "run_results", {})
    print(f"checkpoint={args.checkpoint} success={result.success} validations={len(stats)}")
    if not result.success:
        print("FAILED — open Data Docs (great_expectations/uncommitted/data_docs) for details")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
