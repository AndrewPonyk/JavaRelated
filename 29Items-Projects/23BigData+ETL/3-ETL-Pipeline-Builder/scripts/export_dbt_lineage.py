"""Export dataset lineage from the dbt manifest into a graph JSON.

Runs as the export_lineage task of etl_daily_batch after dbt build. The output
feeds dashboards/docs; Phase 3 replaces this with OpenLineage emission.

Usage:
    python scripts/export_dbt_lineage.py --manifest dbt/target/manifest.json --out docs/lineage
"""

from __future__ import annotations

import argparse
import json
import pathlib
import sys


def build_graph(manifest: dict) -> dict:
    nodes = {}
    edges = []

    for unique_id, node in {**manifest.get("nodes", {}), **manifest.get("sources", {})}.items():
        if node.get("resource_type") not in ("model", "source", "seed", "snapshot"):
            continue
        nodes[unique_id] = {
            "id": unique_id,
            "type": node["resource_type"],
            "name": node.get("name"),
            "schema": node.get("schema"),
            "description": (node.get("description") or "")[:200],
        }

    for child_id, parent_ids in manifest.get("parent_map", {}).items():
        if child_id not in nodes:
            continue
        edges.extend(
            {"from": parent_id, "to": child_id} for parent_id in parent_ids if parent_id in nodes
        )

    return {"nodes": list(nodes.values()), "edges": edges}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", default="dbt/target/manifest.json")
    parser.add_argument("--out", default="docs/lineage")
    args = parser.parse_args()

    manifest_path = pathlib.Path(args.manifest)
    if not manifest_path.exists():
        print(f"manifest not found: {manifest_path} — run `dbt build` (or `dbt parse`) first")
        return 2

    graph = build_graph(json.loads(manifest_path.read_text(encoding="utf-8")))

    out_dir = pathlib.Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    out_file = out_dir / "lineage.json"
    out_file.write_text(json.dumps(graph, indent=2), encoding="utf-8")

    print(f"lineage: {len(graph['nodes'])} datasets, {len(graph['edges'])} edges → {out_file}")
    # TODO(Phase 3): emit OpenLineage events (Marquez/DataHub) and attach the
    # latest GE validation result ids per dataset for quality-aware lineage.
    return 0


if __name__ == "__main__":
    sys.exit(main())
