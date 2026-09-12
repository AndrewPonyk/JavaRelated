"""Alert-rule CRUD — the reference backend endpoint pattern for this codebase.

Router = HTTP shape only. Validation lives in schemas, behavior in the service layer,
storage behind a repository protocol. Domain errors (404/409/503) are mapped to HTTP
in one place — the exception handlers in main.py.
"""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, Path, Query, status

from log_analytics.api.deps import get_alert_rule_service
from log_analytics.api.schemas import (
    AlertRuleCreate,
    AlertRuleList,
    AlertRuleRead,
    AlertRuleUpdate,
)
from log_analytics.api.services.alert_rule_service import AlertRuleService

router = APIRouter(prefix="/alert-rules", tags=["alert-rules"])

ServiceDep = Annotated[AlertRuleService, Depends(get_alert_rule_service)]
# Server-issued ids are UUIDs; rejecting anything else keeps URL metacharacters
# (`/ ? # %`) out of storage-layer request paths entirely.
RuleId = Annotated[str, Path(max_length=64, pattern=r"^[A-Za-z0-9-]+$")]


@router.post("", response_model=AlertRuleRead, status_code=status.HTTP_201_CREATED)
async def create_rule(payload: AlertRuleCreate, service: ServiceDep) -> AlertRuleRead:
    return await service.create(payload)


@router.get("", response_model=AlertRuleList)
async def list_rules(
    service: ServiceDep,
    limit: Annotated[int, Query(ge=1, le=200)] = 50,
    # ≤ the OpenSearch from+size window (10k) — deeper would 400 at the storage layer.
    offset: Annotated[int, Query(ge=0, le=10_000)] = 0,
    enabled: Annotated[bool | None, Query()] = None,
) -> AlertRuleList:
    items, total = await service.list(limit=limit, offset=offset, enabled=enabled)
    return AlertRuleList(items=items, total=total)


@router.get("/{rule_id}", response_model=AlertRuleRead)
async def get_rule(rule_id: RuleId, service: ServiceDep) -> AlertRuleRead:
    return await service.get(rule_id)


@router.put("/{rule_id}", response_model=AlertRuleRead)
async def update_rule(
    rule_id: RuleId, payload: AlertRuleUpdate, service: ServiceDep
) -> AlertRuleRead:
    return await service.update(rule_id, payload)


# response_model=None: with `from __future__ import annotations`, FastAPI would otherwise
# resolve the `-> None` hint to NoneType and reject it as a body on a 204 response.
@router.delete("/{rule_id}", status_code=status.HTTP_204_NO_CONTENT, response_model=None)
async def delete_rule(rule_id: RuleId, service: ServiceDep) -> None:
    await service.delete(rule_id)
