"""Apply migrations: thin wrapper over `alembic upgrade head` (works from any cwd).

Usage:
    python scripts/init_db.py            # uses DATABASE_URL / alembic.ini default
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    print("Applying migrations (alembic upgrade head)…")
    return subprocess.call([sys.executable, "-m", "alembic", "upgrade", "head"], cwd=ROOT)


if __name__ == "__main__":
    raise SystemExit(main())
