"""Self-registration of pipeline outputs in the governance catalog.

After a job materializes a table it registers the dataset (idempotently) and
appends a schema version with the row count. Registration is best-effort:
the pipeline's job is data, so a catalog outage logs a warning and the run
continues — governance reconciles on the next run.

Enabled by CATALOG_API_URL (empty in unit tests → disabled). Uses only the
standard library, keeping the Spark package dependency-light.
"""

from __future__ import annotations

import json
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

from lakehouse.common.config import LakehouseSettings
from lakehouse.common.logging import get_logger

_TIMEOUT_SECONDS = 5


def _request(
    settings: LakehouseSettings, method: str, path: str, payload: dict | None = None
) -> tuple[int, dict[str, Any]]:
    url = f"{settings.catalog_api_url}{path}"
    headers = {"Content-Type": "application/json"}
    if settings.catalog_api_token:
        headers["Authorization"] = f"Bearer {settings.catalog_api_token}"
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=_TIMEOUT_SECONDS) as response:
            return response.status, json.loads(response.read() or b"{}")
    except urllib.error.HTTPError as exc:  # 4xx/5xx still carry a JSON problem body
        body = exc.read()
        try:
            return exc.code, json.loads(body or b"{}")
        except json.JSONDecodeError:
            return exc.code, {"detail": body.decode("utf-8", "replace")}


def _find_dataset_id(settings: LakehouseSettings, name: str) -> str | None:
    query = urllib.parse.urlencode({"name": name, "limit": 1})
    status, body = _request(settings, "GET", f"/api/v1/datasets?{query}")
    if status == 200 and body.get("items"):
        return body["items"][0]["id"]
    return None


def register_dataset_version(
    settings: LakehouseSettings,
    *,
    name: str,
    layer: str,
    s3_path: str,
    schema: dict[str, str],
    row_count: int | None = None,
    description: str | None = None,
) -> bool:
    """Ensure the dataset exists in the catalog and append a schema version.

    Returns True when the version was registered, False when registration is
    disabled or failed (failure is logged, never raised).
    """
    if not settings.catalog_api_url:
        return False

    log = get_logger("catalog", dataset=name, layer=layer)
    try:
        status, body = _request(
            settings,
            "POST",
            "/api/v1/datasets",
            {
                "name": name,
                "layer": layer,
                "description": description,
                "owner_email": settings.catalog_default_owner,
                "s3_path": s3_path,
            },
        )
        if status == 201:
            dataset_id = body["id"]
        elif status == 409:  # already registered — idempotent path
            dataset_id = _find_dataset_id(settings, name)
            if dataset_id is None:
                log.warning("catalog conflict but dataset not found on lookup")
                return False
        else:
            log.warning(
                "catalog registration rejected",
                extra={"context": {"status": status, "detail": body.get("detail")}},
            )
            return False

        status, body = _request(
            settings,
            "POST",
            f"/api/v1/datasets/{dataset_id}/versions",
            {"schema_json": schema, "row_count": row_count},
        )
        if status != 201:
            log.warning(
                "catalog version registration rejected",
                extra={"context": {"status": status, "detail": body.get("detail")}},
            )
            return False

        log.info(
            "registered in catalog",
            extra={"context": {"version": body.get("version"), "row_count": row_count}},
        )
        return True
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        log.warning("catalog unreachable, continuing", extra={"context": {"error": str(exc)}})
        return False
