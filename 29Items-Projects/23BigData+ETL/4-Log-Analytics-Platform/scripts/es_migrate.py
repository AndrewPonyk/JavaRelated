#!/usr/bin/env python
"""Idempotent search-schema migration runner (OpenSearch/Elasticsearch over plain REST).

    python scripts/es_migrate.py [--url http://localhost:9200] [--dry-run] [--skip 0001]

Applied migration ids are recorded in the `la-migrations-state` index; re-running only
applies what's new. Runs in CI/CD before every service deploy (deploy.yml).
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import httpx

STATE_INDEX = "la-migrations-state"
DEFAULT_DIR = Path(__file__).resolve().parent.parent / "elasticsearch" / "migrations"


class MigrationError(RuntimeError):
    pass


def _client(base_url: str) -> httpx.Client:
    auth = None
    username = os.getenv("LA_OPENSEARCH_USERNAME", "")
    if username:
        auth = (username, os.getenv("LA_OPENSEARCH_PASSWORD", ""))
    # TODO(prod): SigV4 signing for AWS OpenSearch domains (requests-aws4auth equivalent).
    return httpx.Client(base_url=base_url.rstrip("/"), auth=auth, timeout=30.0)


def ensure_state_index(client: httpx.Client) -> None:
    if client.head(f"/{STATE_INDEX}").status_code == 200:
        return
    resp = client.put(
        f"/{STATE_INDEX}",
        json={
            "settings": {"number_of_shards": 1, "number_of_replicas": 0},
            "mappings": {
                "dynamic": "strict",
                "properties": {
                    "migration_id": {"type": "keyword"},
                    "description": {"type": "text"},
                    "applied_at": {"type": "date"},
                },
            },
        },
    )
    if resp.status_code not in (200, 201):
        raise MigrationError(f"cannot create state index: {resp.status_code} {resp.text[:300]}")


def applied_ids(client: httpx.Client) -> set[str]:
    resp = client.post(f"/{STATE_INDEX}/_search", json={"size": 1000, "_source": ["migration_id"]})
    if resp.status_code != 200:
        raise MigrationError(f"cannot read state index: {resp.status_code} {resp.text[:300]}")
    hits = resp.json().get("hits", {}).get("hits", [])
    return {h["_source"]["migration_id"] for h in hits}


def record_applied(client: httpx.Client, migration_id: str, description: str) -> None:
    resp = client.put(
        f"/{STATE_INDEX}/_doc/{migration_id}?refresh=true",
        json={
            "migration_id": migration_id,
            "description": description,
            "applied_at": datetime.now(timezone.utc).isoformat(),
        },
    )
    if resp.status_code not in (200, 201):
        raise MigrationError(f"cannot record migration {migration_id}: {resp.text[:300]}")


def apply_migration(client: httpx.Client, migration: dict[str, Any], dry_run: bool) -> None:
    mid, description = migration["id"], migration.get("description", "")
    for request in migration["requests"]:
        method, path = request["method"].upper(), request["path"]
        if dry_run:
            print(f"  [dry-run] {method} {path}")
            continue
        resp = client.request(method, path, json=request.get("body"))
        if resp.status_code >= 300:
            raise MigrationError(
                f"migration {mid}: {method} {path} -> {resp.status_code} {resp.text[:500]}"
            )
    if not dry_run:
        record_applied(client, mid, description)


def load_migrations(directory: Path) -> list[dict[str, Any]]:
    migrations = []
    for file in sorted(directory.glob("*.json")):
        data = json.loads(file.read_text(encoding="utf-8"))
        if "id" not in data or "requests" not in data:
            raise MigrationError(f"{file.name}: migration files need 'id' and 'requests'")
        migrations.append(data)
    return migrations


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--url",
        default=os.getenv(
            "OPENSEARCH_URL", os.getenv("LA_OPENSEARCH_URL", "http://localhost:9200")
        ),
    )
    parser.add_argument("--dir", type=Path, default=DEFAULT_DIR)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument(
        "--skip", default="", help="comma-separated migration ids to skip (e.g. 0001 on vanilla ES)"
    )
    args = parser.parse_args()
    skip = {s.strip() for s in args.skip.split(",") if s.strip()}

    migrations = load_migrations(args.dir)
    with _client(args.url) as client:
        try:
            client.get("/")
        except httpx.HTTPError as exc:
            print(f"ERROR: cannot reach {args.url}: {exc}", file=sys.stderr)
            return 2

        ensure_state_index(client)
        done = applied_ids(client)

        applied = skipped = 0
        for migration in migrations:
            mid = migration["id"]
            if mid in done or mid in skip:
                skipped += 1
                continue
            print(f"applying {mid}: {migration.get('description', '')[:80]}")
            apply_migration(client, migration, args.dry_run)
            applied += 1

    print(f"done — applied {applied}, skipped {skipped} (already applied or --skip)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
