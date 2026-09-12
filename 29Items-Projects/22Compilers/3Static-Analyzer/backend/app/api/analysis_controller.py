from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session

from app.core.config import settings
from app.db.session import get_db
from app.models.analysis import (
    AnalysisCreate,
    AnalysisListItem,
    AnalysisRead,
    AnalysisStatus,
    AnalysisUpdate,
    Finding,
    FindingSeverity,
    HealthRead,
    RuleDefinition,
)
from app.services.analysis_service import AnalysisNotFoundError, AnalysisService
from app.services.rule_service import RuleConfigError
from app.services.sarif_service import SarifService

router = APIRouter(tags=["static-analysis"])


def get_analysis_service(db: Session = Depends(get_db)) -> AnalysisService:
    return AnalysisService(db)


@router.get("/health", response_model=HealthRead)
def health(service: AnalysisService = Depends(get_analysis_service)) -> HealthRead:
    return HealthRead(**service.health())


@router.get("/rules", response_model=list[RuleDefinition])
def list_rules(
    rule_pack: str | None = Query(default=None),
    service: AnalysisService = Depends(get_analysis_service),
) -> list[RuleDefinition]:
    try:
        return service.list_rules(rule_pack)
    except RuleConfigError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc


@router.post("/analyses", response_model=AnalysisRead, status_code=status.HTTP_201_CREATED)
def create_analysis(
    payload: AnalysisCreate,
    service: AnalysisService = Depends(get_analysis_service),
) -> AnalysisRead:
    try:
        return service.create(payload)
    except RuleConfigError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc


@router.get("/analyses", response_model=list[AnalysisListItem])
def list_analyses(
    status_filter: AnalysisStatus | None = Query(default=None, alias="status"),
    severity: FindingSeverity | None = Query(default=None),
    limit: int = Query(default=50, ge=1, le=settings.max_page_size),
    offset: int = Query(default=0, ge=0),
    service: AnalysisService = Depends(get_analysis_service),
) -> list[AnalysisListItem]:
    return service.list(
        status_filter=status_filter,
        severity_filter=severity,
        limit=limit,
        offset=offset,
    )


@router.get("/analyses/{analysis_id}", response_model=AnalysisRead)
def get_analysis(
    analysis_id: UUID,
    service: AnalysisService = Depends(get_analysis_service),
) -> AnalysisRead:
    try:
        return service.get(analysis_id)
    except AnalysisNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc


@router.patch("/analyses/{analysis_id}", response_model=AnalysisRead)
def update_analysis(
    analysis_id: UUID,
    payload: AnalysisUpdate,
    service: AnalysisService = Depends(get_analysis_service),
) -> AnalysisRead:
    try:
        return service.update(analysis_id, payload)
    except AnalysisNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    except RuleConfigError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc


@router.post("/analyses/{analysis_id}/rerun", response_model=AnalysisRead)
def rerun_analysis(
    analysis_id: UUID,
    service: AnalysisService = Depends(get_analysis_service),
) -> AnalysisRead:
    try:
        return service.rerun(analysis_id)
    except AnalysisNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc


@router.get("/analyses/{analysis_id}/findings", response_model=list[Finding])
def list_findings(
    analysis_id: UUID,
    severity: FindingSeverity | None = Query(default=None),
    limit: int = Query(default=100, ge=1, le=settings.max_page_size),
    offset: int = Query(default=0, ge=0),
    service: AnalysisService = Depends(get_analysis_service),
) -> list[Finding]:
    try:
        return service.list_findings(
            analysis_id,
            severity_filter=severity,
            limit=limit,
            offset=offset,
        )
    except AnalysisNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc


@router.get("/analyses/{analysis_id}/sarif")
def export_sarif(
    analysis_id: UUID,
    service: AnalysisService = Depends(get_analysis_service),
) -> JSONResponse:
    try:
        analysis = service.get(analysis_id)
    except AnalysisNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    return JSONResponse(content=SarifService().build(analysis), media_type="application/sarif+json")


@router.delete("/analyses/{analysis_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_analysis(
    analysis_id: UUID,
    service: AnalysisService = Depends(get_analysis_service),
) -> None:
    try:
        service.delete(analysis_id)
    except AnalysisNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
