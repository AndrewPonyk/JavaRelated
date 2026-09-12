"""Export the FastAPI OpenAPI specification to docs/api/openapi.json.

Usage:
    PYTHONPATH=src python scripts/export_openapi.py
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from fraud_detection.main import create_app  # noqa: E402

OUTPUT = PROJECT_ROOT / "docs" / "api" / "openapi.json"


def main() -> int:
    """Write the pretty-printed OpenAPI document; returns an exit code."""
    spec = create_app().openapi()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(spec, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Wrote {OUTPUT} ({len(spec.get('paths', {}))} paths)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
