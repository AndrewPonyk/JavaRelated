"""Read-only Trino access for dataset previews in the Console.

Security posture: the API's Trino user is read-only, identifiers are validated
against a strict pattern and double-quoted (never interpolated from raw user
input — they come from catalog records), and previews are LIMIT-capped.
"""

from __future__ import annotations

import re
from collections.abc import Callable
from dataclasses import dataclass
from functools import lru_cache
from typing import Any

from app.core.config import get_settings
from app.db.models import Dataset

IDENTIFIER_PATTERN = re.compile(r"^[a-z][a-z0-9_]*$")


class TrinoUnavailableError(Exception):
    """Raised when Trino cannot be reached or the query fails — mapped to 502."""


@dataclass(frozen=True)
class PreviewResult:
    columns: list[str]
    rows: list[list[Any]]
    source: str


def _quote_identifier(name: str) -> str:
    if not IDENTIFIER_PATTERN.fullmatch(name):
        raise ValueError(f"invalid identifier: {name!r}")
    return f'"{name}"'


def resolve_trino_table(dataset: Dataset) -> tuple[str, str]:
    """Catalog naming convention: dataset `sales.orders` in layer `silver`
    is registered in Trino as schema=`silver`, table=`orders` (matching the
    dbt sources layout in transform/dbt)."""
    table = dataset.name.split(".")[-1]
    return dataset.layer.value, table


class TrinoPreviewService:
    """`connection_factory` is injectable so tests run without a Trino server."""

    def __init__(self, connection_factory: Callable[[], Any] | None = None) -> None:
        self._connection_factory = connection_factory or self._default_factory

    @staticmethod
    def _default_factory() -> Any:
        import trino

        settings = get_settings()
        return trino.dbapi.connect(
            host=settings.trino_host,
            port=settings.trino_port,
            user=settings.trino_user,
            catalog=settings.trino_catalog,
            http_scheme=settings.trino_http_scheme,
        )

    def preview(self, dataset: Dataset, limit: int) -> PreviewResult:
        settings = get_settings()
        limit = max(1, min(int(limit), settings.preview_row_limit))
        schema, table = resolve_trino_table(dataset)
        source = f"{settings.trino_catalog}.{schema}.{table}"
        sql = f"SELECT * FROM {_quote_identifier(schema)}.{_quote_identifier(table)} LIMIT {limit}"  # noqa: S608 — identifiers validated above

        try:
            connection = self._connection_factory()
            try:
                cursor = connection.cursor()
                cursor.execute(sql)
                columns = [col[0] for col in cursor.description or []]
                rows = [list(row) for row in cursor.fetchall()]
            finally:
                connection.close()
        except (ValueError, TypeError):
            raise
        except Exception as exc:  # trino/network errors → typed domain error
            raise TrinoUnavailableError(
                f"Could not preview {source} via Trino at "
                f"{settings.trino_host}:{settings.trino_port}: {exc}"
            ) from exc

        return PreviewResult(columns=columns, rows=rows, source=source)


@lru_cache
def get_preview_service() -> TrinoPreviewService:
    return TrinoPreviewService()
