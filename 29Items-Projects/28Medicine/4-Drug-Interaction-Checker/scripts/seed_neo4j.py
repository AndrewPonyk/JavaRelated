#!/usr/bin/env python3
"""Apply Neo4j migration scripts in order.

Usage:
    python scripts/seed_neo4j.py                # schema + seed
    python scripts/seed_neo4j.py --schema-only  # constraints/indexes only

Connection comes from env: NEO4J_URI, NEO4J_USER, NEO4J_PASSWORD.
Requires: pip install neo4j
"""
from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

try:
    from neo4j import GraphDatabase
except ImportError:  # pragma: no cover
    sys.exit("The 'neo4j' package is required: pip install neo4j")

MIGRATIONS_DIR = Path(__file__).resolve().parent.parent / "migrations" / "neo4j"


def _statements(cypher: str):
    """Split a Cypher script into statements on `;`.

    Quote-aware so semicolons inside string literals (e.g. "risk; monitor INR")
    are not treated as separators, and `//` line comments are stripped first so
    semicolons inside comments don't break the split.
    """
    lines = [ln for ln in cypher.splitlines() if not ln.strip().startswith("//")]
    body = "\n".join(lines)

    buf: list[str] = []
    quote: str | None = None
    i = 0
    while i < len(body):
        ch = body[i]
        if quote is not None:
            buf.append(ch)
            if ch == "\\" and i + 1 < len(body):
                buf.append(body[i + 1])
                i += 2
                continue
            if ch == quote:
                quote = None
        elif ch in ('"', "'"):
            quote = ch
            buf.append(ch)
        elif ch == ";":
            stmt = "".join(buf).strip()
            if stmt:
                yield stmt
            buf = []
        else:
            buf.append(ch)
        i += 1

    tail = "".join(buf).strip()
    if tail:
        yield tail


def apply_file(session, path: Path) -> None:
    print(f"-> applying {path.name}")
    for stmt in _statements(path.read_text(encoding="utf-8")):
        session.run(stmt)


def main() -> None:
    parser = argparse.ArgumentParser(description="Apply Neo4j migrations")
    parser.add_argument("--schema-only", action="store_true", help="Skip seed data")
    args = parser.parse_args()

    uri = os.getenv("NEO4J_URI", "bolt://localhost:7687")
    user = os.getenv("NEO4J_USER", "neo4j")
    password = os.getenv("NEO4J_PASSWORD", "password")

    files = sorted(MIGRATIONS_DIR.glob("*.cypher"))
    if args.schema_only:
        files = [f for f in files if "seed" not in f.name]

    driver = GraphDatabase.driver(uri, auth=(user, password))
    try:
        with driver.session() as session:
            for path in files:
                apply_file(session, path)
        print("Done.")
    finally:
        driver.close()


if __name__ == "__main__":
    main()
