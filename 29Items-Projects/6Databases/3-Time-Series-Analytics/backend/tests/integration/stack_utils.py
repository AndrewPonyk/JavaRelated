"""Helpers for tests that need a LIVE Cassandra/Redis (marker: requires_stack).

Locally: `docker compose up -d cassandra redis` first; tests skip fast when
the stack is absent. CI provides the services and sets TSA_STACK_RETRIES so
the fixture waits out Cassandra's slow boot.
"""

from __future__ import annotations

import os
import time
from pathlib import Path

import pytest

MIGRATIONS_DIR = Path(__file__).resolve().parents[2] / "migrations" / "cassandra"


def _split_statements(text: str) -> list[str]:
    """Split CQL text into statements.

    Handles what naive splitting gets wrong: `--` comments (full-line AND
    inline) are stripped, and `;` inside single-quoted strings (table comments
    legitimately contain semicolons) does not split.
    """
    statements: list[str] = []
    current: list[str] = []
    in_string = False
    i = 0
    while i < len(text):
        char = text[i]
        if char == "'":
            in_string = not in_string  # '' escapes toggle twice — net no-op
            current.append(char)
        elif not in_string and text[i : i + 2] == "--":
            while i < len(text) and text[i] != "\n":
                i += 1
            continue  # keep the newline itself
        elif char == ";" and not in_string:
            statement = "".join(current).strip()
            if statement:
                statements.append(statement)
            current = []
        else:
            current.append(char)
        i += 1
    trailing = "".join(current).strip()
    if trailing:
        statements.append(trailing)
    return statements


def cql_statements() -> list[str]:
    """All migration statements, in file order."""
    statements: list[str] = []
    for path in sorted(MIGRATIONS_DIR.glob("*.cql")):
        statements.extend(_split_statements(path.read_text(encoding="utf-8")))
    return statements


def connect_cassandra_or_skip():
    from cassandra.cluster import Cluster

    host = os.environ.get("CASSANDRA_CONTACT_POINTS", "127.0.0.1").split(",")[0].strip()
    port = int(os.environ.get("CASSANDRA_PORT", "9042"))
    retries = int(os.environ.get("TSA_STACK_RETRIES", "1"))

    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            cluster = Cluster([host], port=port, connect_timeout=5)
            return cluster, cluster.connect()
        except Exception as exc:  # noqa: BLE001 — any failure means "not up yet"
            last_error = exc
            if attempt < retries - 1:
                time.sleep(5)
    pytest.skip(f"Cassandra not reachable at {host}:{port} ({last_error})")


def check_redis_or_skip() -> None:
    import redis as redis_pkg

    url = os.environ.get("REDIS_URL", "redis://127.0.0.1:6379/0")
    try:
        client = redis_pkg.Redis.from_url(url, socket_connect_timeout=3)
        client.ping()
        client.close()
    except Exception as exc:
        pytest.skip(f"Redis not reachable at {url} ({exc})")
