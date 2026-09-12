"""Integration: migration runner applies against a live OpenSearch and is idempotent.

Needs `docker compose up -d opensearch` (skips otherwise). Run: pytest -m integration
"""

from __future__ import annotations

import os

import httpx
import pytest

pytestmark = pytest.mark.integration

OPENSEARCH_URL = os.getenv(
    "LA_OPENSEARCH_URL", os.getenv("OPENSEARCH_URL", "http://localhost:9200")
)


def _reachable(url: str) -> bool:
    try:
        return httpx.get(url, timeout=2.0).status_code == 200
    except httpx.HTTPError:
        return False


@pytest.fixture(scope="module", autouse=True)
def require_opensearch() -> None:
    if not _reachable(OPENSEARCH_URL):
        pytest.skip(f"OpenSearch not reachable at {OPENSEARCH_URL}")


def test_migrations_apply_and_are_idempotent(capsys: pytest.CaptureFixture) -> None:
    import es_migrate  # scripts/ is on pythonpath (pyproject pytest config)

    migrations = es_migrate.load_migrations(es_migrate.DEFAULT_DIR)
    assert migrations, "no migrations found"

    with es_migrate._client(OPENSEARCH_URL) as client:
        es_migrate.ensure_state_index(client)
        already = es_migrate.applied_ids(client)
        for migration in migrations:
            if migration["id"] not in already:
                es_migrate.apply_migration(client, migration, dry_run=False)

        # Second pass: everything must be recorded, nothing to redo.
        assert {m["id"] for m in migrations} <= es_migrate.applied_ids(client)

        # Write alias exists and points at the bootstrap index.
        alias = client.get("/_alias/la-logs")
        assert alias.status_code == 200
        assert "la-logs-000001" in alias.json()
