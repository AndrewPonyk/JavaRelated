"""Core orchestration: normalize drugs, look up known interactions, and
optionally enrich with ML severity predictions for uncurated pairs."""

from itertools import combinations

from app.core.config import get_settings
from app.core.exceptions import DrugNotFoundError, TooManyDrugsError
from app.core.logging import get_logger
from app.db.repositories.drug_repository import DrugRepository
from app.db.repositories.interaction_repository import InteractionRepository
from app.models.common import SEVERITY_ORDER, EvidenceLevel, Severity
from app.models.drug import Drug
from app.models.interaction import (
    InteractionCheckRequest,
    InteractionCheckResponse,
    InteractionListResponse,
    InteractionPair,
    InteractionUpsert,
)
from app.services.ml_severity_service import MLSeverityService
from app.services.rxnorm_service import RxNormService

logger = get_logger(__name__)


def _row_to_pair(row: dict) -> InteractionPair:
    evidence = row.get("evidence_level")
    return InteractionPair(
        rxcui_a=row["rxcui_a"],
        rxcui_b=row["rxcui_b"],
        name_a=row["name_a"],
        name_b=row["name_b"],
        severity=Severity(row.get("severity") or "unknown"),
        evidence_level=EvidenceLevel(evidence) if evidence else None,
        mechanism=row.get("mechanism"),
        description=row.get("description"),
        source=row.get("source"),
    )


class InteractionService:
    def __init__(
        self,
        interaction_repo: InteractionRepository,
        drug_repo: DrugRepository,
        rxnorm: RxNormService,
        ml: MLSeverityService,
    ) -> None:
        self._interactions = interaction_repo
        self._drugs = drug_repo
        self._rxnorm = rxnorm
        self._ml = ml

    # ---- interaction check (core feature) ----------------------------------
    async def check(self, request: InteractionCheckRequest) -> InteractionCheckResponse:
        settings = get_settings()
        if len(request.drugs) > settings.max_drugs_per_check:
            raise TooManyDrugsError(f"At most {settings.max_drugs_per_check} drugs per check.")

        resolved: dict[str, Drug] = {}
        unresolved: list[str] = []
        for item in request.drugs:
            drug = await self._rxnorm.resolve(rxcui=item.rxcui, ndc=item.ndc, name=item.name)
            if drug is None:
                unresolved.append(item.label())
            else:
                resolved[drug.rxcui] = drug

        names = self._ingredient_index(resolved)
        rxcuis = list(names.keys())

        known_rows = await self._interactions.find_interactions_for_set(rxcuis)
        known_pairs = {frozenset((r["rxcui_a"], r["rxcui_b"])) for r in known_rows}
        results = [_row_to_pair(r) for r in known_rows]

        if request.include_ml_prediction and settings.ml_enabled:
            results.extend(await self._predict_unknown(rxcuis, known_pairs, names))

        results.sort(key=lambda p: SEVERITY_ORDER[p.severity], reverse=True)
        response = InteractionCheckResponse(
            checked_drugs=[d.name for d in resolved.values()],
            unresolved=unresolved,
            interactions=results,
            highest_severity=self._highest_severity(results),
        )
        # Audit log (no PHI): what was checked and the worst finding.
        logger.info(
            "interaction_check",
            drug_count=len(resolved),
            unresolved_count=len(unresolved),
            interaction_count=len(results),
            highest_severity=response.highest_severity.value,
        )
        return response

    @staticmethod
    def _ingredient_index(resolved: dict[str, Drug]) -> dict[str, str]:
        """Map ingredient RxCUI -> display name (falls back to the drug itself)."""
        index: dict[str, str] = {}
        for drug in resolved.values():
            if drug.ingredients:
                for ing in drug.ingredients:
                    index[ing.rxcui] = ing.name
            else:
                index[drug.rxcui] = drug.name
        return index

    async def _predict_unknown(
        self, rxcuis: list[str], known_pairs: set[frozenset], names: dict[str, str]
    ) -> list[InteractionPair]:
        settings = get_settings()
        unknown = [
            (a, b)
            for a, b in combinations(sorted(rxcuis), 2)
            if frozenset((a, b)) not in known_pairs
        ]
        if not unknown:
            return []
        if len(unknown) > settings.max_pairs_for_ml:
            logger.warning("ml_pairs_capped", requested=len(unknown), cap=settings.max_pairs_for_ml)
            unknown = unknown[: settings.max_pairs_for_ml]

        predictions = await self._ml.predict_batch(unknown)
        out: list[InteractionPair] = []
        for (a, b), pred in predictions.items():
            if pred.severity == Severity.UNKNOWN:
                continue
            out.append(
                InteractionPair(
                    rxcui_a=a,
                    rxcui_b=b,
                    name_a=names.get(a, a),
                    name_b=names.get(b, b),
                    severity=pred.severity,
                    mechanism=pred.mechanism,
                    description="Predicted interaction - not clinically confirmed.",
                    source="ml-severity-model",
                    ml_predicted=True,
                    ml_confidence=pred.confidence,
                )
            )
        return out

    @staticmethod
    def _highest_severity(pairs: list[InteractionPair]) -> Severity:
        if not pairs:
            return Severity.UNKNOWN
        return max(pairs, key=lambda p: SEVERITY_ORDER[p.severity]).severity

    # ---- admin / CRUD ------------------------------------------------------
    async def create_interaction(self, payload: InteractionUpsert) -> InteractionPair:
        written = await self._interactions.upsert_interaction(
            payload.rxcui_a,
            payload.rxcui_b,
            severity=payload.severity.value,
            mechanism=payload.mechanism,
            evidence_level=payload.evidence_level.value if payload.evidence_level else None,
            description=payload.description,
            source=payload.source,
        )
        if not written:
            raise DrugNotFoundError("Both ingredients must exist before creating an interaction.")
        row = await self._interactions.find_interaction(payload.rxcui_a, payload.rxcui_b)
        return _row_to_pair(row)  # type: ignore[arg-type]

    async def list_interactions(self, limit: int = 50, offset: int = 0) -> InteractionListResponse:
        rows, total = await self._interactions.list_interactions(limit=limit, offset=offset)
        return InteractionListResponse(
            items=[_row_to_pair(r) for r in rows], total=total, limit=limit, offset=offset
        )

    async def delete_interaction(self, rxcui_a: str, rxcui_b: str) -> bool:
        return await self._interactions.delete_interaction(rxcui_a, rxcui_b)
