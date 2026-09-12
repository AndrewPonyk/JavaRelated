"""Regenerate bundled demo datasets (deterministic — same seed, same CSV).

Usage:
    python scripts/seed_demo_data.py            # write data/samples/*.csv
    python scripts/seed_demo_data.py --to-db    # also register in the library (needs DATABASE_URL)

The generated A/B dataset has real effects baked in (variant B converts better
and has longer sessions), so every analysis page has something to find.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
import pandas as pd

sys.path.append(str(Path(__file__).resolve().parents[1]))

SAMPLES_DIR = Path(__file__).resolve().parents[1] / "data" / "samples"

SEED = 42
N_PER_VARIANT = 400
CONVERSION = {"A": 0.10, "B": 0.13}
SESSION_SHIFT_MIN = {"A": 0.0, "B": 1.5}  # gives the t-test something to find


def build_ab_test_demo() -> pd.DataFrame:
    rng = np.random.default_rng(SEED)
    frames = []
    for variant in ("A", "B"):
        converted = rng.binomial(1, CONVERSION[variant], N_PER_VARIANT)
        session = np.clip(
            rng.normal(12.0 + SESSION_SHIFT_MIN[variant], 4.0, N_PER_VARIANT), 0.5, None
        )
        revenue = np.where(converted == 1, np.round(rng.lognormal(3.0, 0.5, N_PER_VARIANT), 2), 0.0)
        age_group = rng.choice(
            ["18-24", "25-34", "35-44", "45+"], N_PER_VARIANT, p=[0.25, 0.40, 0.22, 0.13]
        )
        frames.append(
            pd.DataFrame(
                {
                    "variant": variant,
                    "converted": converted.astype(int),
                    "revenue": revenue,
                    "session_minutes": np.round(session, 2),
                    "age_group": age_group,
                }
            )
        )
    df = pd.concat(frames, ignore_index=True)
    df.insert(0, "user_id", np.arange(1, len(df) + 1))
    return df


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--to-db",
        action="store_true",
        help="also register the dataset in the library (requires DATABASE_URL)",
    )
    args = parser.parse_args()

    SAMPLES_DIR.mkdir(parents=True, exist_ok=True)
    df = build_ab_test_demo()
    out = SAMPLES_DIR / "ab_test_demo.csv"
    df.to_csv(out, index=False)
    print(f"Wrote {out} ({len(df)} rows, {df.shape[1]} columns)")

    if args.to_db:
        from app.core.config import get_settings
        from app.services import dataset_service

        if get_settings().demo_mode:
            print("DATABASE_URL not configured — skipping library registration.")
            return
        dataset_id = dataset_service.save_dataset(
            df,
            "ab_test_demo",
            description="Bundled demo A/B experiment (seeded, regenerable)",
            source_type="demo",
        )
        print(f"Registered in the library as {dataset_id} (deduplicated by content hash).")


if __name__ == "__main__":
    main()
