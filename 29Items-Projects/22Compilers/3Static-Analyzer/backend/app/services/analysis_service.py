from __future__ import annotations

import json
from datetime import UTC, datetime
from uuid import UUID, uuid4

from sqlalchemy import delete, exists, func, select
from sqlalchemy.orm import Session, selectinload

from app.db.entities import AnalysisJobEntity, FindingEntity, RuleDefinitionEntity
from app.models.analysis import (
    AnalysisCreate,
    AnalysisListItem,
    AnalysisRead,
    AnalysisStatus,
    AnalysisUpdate,
    Finding,
    FindingSeverity,
    RuleDefinition,
)
from app.services.rule_service import RuleConfigError, RuleService
from app.services.source_analyzer import SourceAnalysisError, SourceAnalyzer, SourceInput


class AnalysisNotFoundError(Exception):
    """Raised when an analysis job cannot be found."""


class AnalysisService:
    def __init__(
        self,
        db: Session,
        rule_service: RuleService | None = None,
        source_analyzer: SourceAnalyzer | None = None,
    ) -> None:
        self.db = db
        self.rule_service = rule_service or RuleService()
        self.source_analyzer = source_analyzer or SourceAnalyzer()

    def create(self, payload: AnalysisCreate) -> AnalysisRead:
        self.rule_service.enabled_rules(payload.rule_pack)
        analysis = AnalysisJobEntity(
            id=str(uuid4()),
            project_name=payload.project_name,
            source_path=payload.source_path,
            compile_commands_path=payload.compile_commands_path,
            rule_pack=payload.rule_pack,
            status=AnalysisStatus.queued.value,
            ast_facts_json="{}",
            created_at=datetime.now(UTC),
            updated_at=datetime.now(UTC),
        )
        self.db.add(analysis)
        self.db.commit()

        try:
            self._run_analysis(analysis, payload)
        except (SourceAnalysisError, ValueError) as exc:
            self._mark_failed(analysis, exc)
        return self.get(UUID(analysis.id))

    def list(
        self,
        status_filter: AnalysisStatus | None = None,
        severity_filter: FindingSeverity | None = None,
        limit: int = 50,
        offset: int = 0,
    ) -> list[AnalysisListItem]:
        statement = select(AnalysisJobEntity).options(selectinload(AnalysisJobEntity.findings))
        if status_filter is not None:
            statement = statement.where(AnalysisJobEntity.status == status_filter.value)
        if severity_filter is not None:
            statement = statement.where(
                exists()
                .where(FindingEntity.analysis_id == AnalysisJobEntity.id)
                .where(FindingEntity.severity == severity_filter.value)
            )
        statement = statement.order_by(AnalysisJobEntity.created_at.desc())
        statement = statement.limit(limit).offset(offset)

        rows = self.db.scalars(statement).all()
        return [self._to_list_item(row) for row in rows]

    def get(self, analysis_id: UUID) -> AnalysisRead:
        entity = self._get_entity(analysis_id)
        return self._to_read(entity)

    def update(self, analysis_id: UUID, payload: AnalysisUpdate) -> AnalysisRead:
        entity = self._get_entity(analysis_id)
        if payload.project_name is not None:
            entity.project_name = payload.project_name
        if payload.rule_pack is not None:
            self.rule_service.enabled_rules(payload.rule_pack)
            entity.rule_pack = payload.rule_pack
        if payload.status is not None:
            entity.status = payload.status.value
        entity.updated_at = datetime.now(UTC)
        self.db.commit()
        return self.get(analysis_id)

    def rerun(self, analysis_id: UUID) -> AnalysisRead:
        entity = self._get_entity(analysis_id)
        source = AnalysisCreate(
            project_name=entity.project_name,
            source_path=entity.source_path,
            compile_commands_path=entity.compile_commands_path,
            rule_pack=entity.rule_pack,
            source_code=None,
        )
        try:
            self._run_analysis(entity, source)
        except (RuleConfigError, SourceAnalysisError, ValueError) as exc:
            self._mark_failed(entity, exc)
        return self.get(analysis_id)

    def delete(self, analysis_id: UUID) -> None:
        entity = self._get_entity(analysis_id)
        self.db.delete(entity)
        self.db.commit()

    def list_findings(
        self,
        analysis_id: UUID,
        severity_filter: FindingSeverity | None = None,
        limit: int = 100,
        offset: int = 0,
    ) -> list[Finding]:
        self._get_entity(analysis_id)
        statement = select(FindingEntity).where(FindingEntity.analysis_id == str(analysis_id))
        if severity_filter is not None:
            statement = statement.where(FindingEntity.severity == severity_filter.value)
        statement = statement.order_by(FindingEntity.severity.desc(), FindingEntity.line.asc())
        statement = statement.limit(limit).offset(offset)
        return [self._to_finding(entity) for entity in self.db.scalars(statement).all()]

    def list_rules(self, rule_pack: str | None = None) -> list[RuleDefinition]:
        rules = self.rule_service.list_rules(rule_pack)
        self._sync_rules(rules)
        return rules

    def health(self) -> dict[str, str | int]:
        self.db.execute(select(func.count()).select_from(AnalysisJobEntity)).scalar_one()
        return {
            "status": "ok",
            "database": "ok",
            "rules_loaded": len(self.rule_service.list_rules()),
        }

    def _run_analysis(self, entity: AnalysisJobEntity, payload: AnalysisCreate) -> None:
        rules = self.rule_service.enabled_rules(payload.rule_pack)
        self._sync_rules(rules)
        entity.status = AnalysisStatus.running.value
        entity.error_message = None
        entity.updated_at = datetime.now(UTC)
        self.db.execute(delete(FindingEntity).where(FindingEntity.analysis_id == entity.id))
        self.db.commit()

        facts, findings = self.source_analyzer.evaluate(
            SourceInput(
                source_path=payload.source_path,
                compile_commands_path=payload.compile_commands_path,
                source_code=payload.source_code,
            ),
            rules,
        )
        entity.ast_facts_json = json.dumps(facts, sort_keys=True)
        entity.status = AnalysisStatus.completed.value
        entity.updated_at = datetime.now(UTC)

        for finding in findings:
            self.db.add(
                FindingEntity(
                    id=str(finding.id),
                    analysis_id=entity.id,
                    rule_id=finding.rule_id,
                    severity=finding.severity.value,
                    message=finding.message,
                    file_path=finding.file_path,
                    line=finding.line,
                    column=finding.column,
                    evidence_json=json.dumps(finding.evidence, sort_keys=True),
                )
            )
        self.db.commit()

    def _mark_failed(self, entity: AnalysisJobEntity, exc: Exception) -> None:
        entity.status = AnalysisStatus.failed.value
        entity.error_message = str(exc)
        entity.updated_at = datetime.now(UTC)
        self.db.commit()

    def _sync_rules(self, rules: list[RuleDefinition]) -> None:
        for rule in rules:
            existing = self.db.get(RuleDefinitionEntity, rule.id)
            if existing is None:
                self.db.add(
                    RuleDefinitionEntity(
                        id=rule.id,
                        rule_pack=rule.rule_pack,
                        severity=rule.severity.value,
                        description=rule.description,
                        enabled=1 if rule.enabled else 0,
                    )
                )
            else:
                existing.rule_pack = rule.rule_pack
                existing.severity = rule.severity.value
                existing.description = rule.description
                existing.enabled = 1 if rule.enabled else 0
        self.db.commit()

    def _get_entity(self, analysis_id: UUID) -> AnalysisJobEntity:
        entity = self.db.scalar(
            select(AnalysisJobEntity)
            .where(AnalysisJobEntity.id == str(analysis_id))
            .options(selectinload(AnalysisJobEntity.findings))
        )
        if entity is None:
            raise AnalysisNotFoundError(f"Analysis {analysis_id} was not found")
        return entity

    def _to_read(self, entity: AnalysisJobEntity) -> AnalysisRead:
        return AnalysisRead(
            id=UUID(entity.id),
            project_name=entity.project_name,
            source_path=entity.source_path,
            compile_commands_path=entity.compile_commands_path,
            rule_pack=entity.rule_pack,
            status=AnalysisStatus(entity.status),
            findings=[self._to_finding(finding) for finding in entity.findings],
            ast_facts=json.loads(entity.ast_facts_json or "{}"),
            error_message=entity.error_message,
            created_at=entity.created_at,
            updated_at=entity.updated_at,
        )

    def _to_finding(self, entity: FindingEntity) -> Finding:
        return Finding(
            id=UUID(entity.id),
            rule_id=entity.rule_id,
            severity=FindingSeverity(entity.severity),
            message=entity.message,
            file_path=entity.file_path,
            line=entity.line,
            column=entity.column,
            evidence=json.loads(entity.evidence_json or "{}"),
        )

    def _to_list_item(self, entity: AnalysisJobEntity) -> AnalysisListItem:
        severities = [FindingSeverity(finding.severity) for finding in entity.findings]
        return AnalysisListItem(
            id=UUID(entity.id),
            project_name=entity.project_name,
            source_path=entity.source_path,
            rule_pack=entity.rule_pack,
            status=AnalysisStatus(entity.status),
            finding_count=len(entity.findings),
            highest_severity=max(severities, key=self._severity_rank) if severities else None,
            created_at=entity.created_at,
            updated_at=entity.updated_at,
        )

    def _severity_rank(self, severity: FindingSeverity) -> int:
        return {
            FindingSeverity.info: 0,
            FindingSeverity.low: 1,
            FindingSeverity.medium: 2,
            FindingSeverity.high: 3,
            FindingSeverity.critical: 4,
        }[severity]
