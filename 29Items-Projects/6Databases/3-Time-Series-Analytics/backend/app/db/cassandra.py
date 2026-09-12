"""Cassandra session lifecycle.

The DataStax driver is synchronous — every call from async code MUST go through
`execute()` (thread offload) or it will freeze the event loop
(docs/TECH-NOTES.md §3.6 pitfall 3).
"""

from __future__ import annotations

import asyncio
import logging
from typing import Any

from app.core.config import settings
from app.db import BackendUnavailableError

logger = logging.getLogger(__name__)

_cluster: Any = None
_session: Any = None
_prepared: dict[str, Any] = {}


async def connect() -> None:
    global _cluster, _session
    if _session is not None:
        return

    from cassandra.cluster import EXEC_PROFILE_DEFAULT, Cluster, ExecutionProfile
    from cassandra.policies import DCAwareRoundRobinPolicy, TokenAwarePolicy

    def _sync_connect() -> tuple[Any, Any]:
        profile = ExecutionProfile(
            load_balancing_policy=TokenAwarePolicy(
                DCAwareRoundRobinPolicy(local_dc=settings.cassandra_local_dc)
            ),
            request_timeout=10.0,
        )
        auth_provider = None
        if settings.cassandra_username:
            from cassandra.auth import PlainTextAuthProvider

            auth_provider = PlainTextAuthProvider(
                settings.cassandra_username, settings.cassandra_password
            )
        # Client-to-node TLS (ssl_context) is part of the Phase 3 hardening
        # item in PROJECT-PLAN.md; in current topologies Cassandra listens on
        # the private VPC network only (docs/ARCHITECTURE.md §2.5).
        cluster = Cluster(
            contact_points=settings.cassandra_contact_points_list,
            port=settings.cassandra_port,
            execution_profiles={EXEC_PROFILE_DEFAULT: profile},
            auth_provider=auth_provider,
        )
        return cluster, cluster.connect(settings.cassandra_keyspace)

    _cluster, _session = await asyncio.to_thread(_sync_connect)


def get_session() -> Any:
    if _session is None:
        raise BackendUnavailableError("Cassandra is not available")
    return _session


async def _prepare(query: str) -> Any:
    session = get_session()
    stmt = _prepared.get(query)
    if stmt is None:
        stmt = await asyncio.to_thread(session.prepare, query)
        _prepared[query] = stmt
    return stmt


async def execute(query: str, params: tuple | list | None = None) -> Any:
    """Execute a statement off the event loop; prepared statements are cached."""
    session = get_session()
    if params is not None:
        stmt = await _prepare(query)
        return await asyncio.to_thread(session.execute, stmt, params)
    return await asyncio.to_thread(session.execute, query)


async def execute_unlogged_batch(query: str, param_sets: list[tuple]) -> Any:
    """Batch one prepared statement over many rows of ONE partition.

    Callers must guarantee all param sets target the same partition —
    single-partition unlogged batches are the only batch shape that helps in
    Cassandra; cross-partition batches are an anti-pattern.
    """
    from cassandra.query import BatchStatement, BatchType

    session = get_session()
    stmt = await _prepare(query)
    batch = BatchStatement(batch_type=BatchType.UNLOGGED)
    for params in param_sets:
        batch.add(stmt, params)
    return await asyncio.to_thread(session.execute, batch)


async def close() -> None:
    global _cluster, _session
    if _cluster is not None:
        await asyncio.to_thread(_cluster.shutdown)
    _cluster = None
    _session = None
    _prepared.clear()
