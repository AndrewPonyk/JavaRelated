"""Reusable in-memory test doubles (no Neo4j / network required)."""

from __future__ import annotations

from app.models.common import Severity
from app.models.drug import Drug, DrugListResponse, DrugSearchResult, Ingredient
from app.models.interaction import (
    InteractionCheckResponse,
    InteractionListResponse,
    InteractionPair,
)


# --------------------------------------------------------------------------- #
# Fake Neo4j driver / session / result
# --------------------------------------------------------------------------- #
class FakeRecord(dict):
    """A Neo4j-record stand-in: dict(record) and record["k"] both work."""


class FakeResult:
    def __init__(self, records=None, single=None) -> None:
        self._records = list(records) if records is not None else []
        self._single = single

    async def single(self):
        if self._single is not None:
            return self._single
        return self._records[0] if self._records else None

    def __aiter__(self):
        self._iter = iter(self._records)
        return self

    async def __anext__(self):
        try:
            return next(self._iter)
        except StopIteration:
            raise StopAsyncIteration from None


class FakeSession:
    def __init__(self, results) -> None:
        self._results = results
        self._i = 0
        self.runs: list[tuple[str, dict]] = []

    async def __aenter__(self):
        return self

    async def __aexit__(self, *exc):
        return False

    async def run(self, query, **params):
        self.runs.append((query, params))
        if isinstance(self._results, list):
            result = self._results[self._i] if self._i < len(self._results) else FakeResult()
            self._i += 1
            return result
        return self._results


class FakeDriver:
    def __init__(self, results=None) -> None:
        self._results = results if results is not None else FakeResult()
        self.sessions: list[FakeSession] = []

    def session(self, **kwargs):
        session = FakeSession(self._results)
        self.sessions.append(session)
        return session

    async def verify_connectivity(self):
        return None

    async def close(self):
        return None


# --------------------------------------------------------------------------- #
# Fake external clients
# --------------------------------------------------------------------------- #
class FakeRxNormClient:
    """Minimal RxNorm client; tracks call counts to assert caching."""

    def __init__(self) -> None:
        self.props_calls = 0
        self.name_calls = 0

    async def find_rxcui_by_name(self, name: str):
        self.name_calls += 1
        return "1191" if name.strip().lower() == "aspirin" else None

    async def find_rxcui_by_ndc(self, ndc: str):
        return "1191" if ndc == "good-ndc" else None

    async def get_drug_properties(self, rxcui: str):
        self.props_calls += 1
        return {"name": "aspirin", "tty": "IN"} if rxcui == "1191" else None

    async def get_ingredients(self, rxcui: str):
        return [{"rxcui": "1191", "name": "aspirin"}]


# --------------------------------------------------------------------------- #
# Fake services (for API-layer dependency overrides)
# --------------------------------------------------------------------------- #
class FakeDrugService:
    def __init__(self, *, drug=None, search=None, deleted=True, raise_get=False) -> None:
        self._drug = drug
        self._search = search
        self._deleted = deleted
        self._raise_get = raise_get

    async def get(self, rxcui: str) -> Drug:
        if self._raise_get:
            from app.core.exceptions import DrugNotFoundError

            raise DrugNotFoundError(f"No drug found for rxcui={rxcui}")
        return self._drug or Drug(rxcui=rxcui, name="aspirin", tty="IN")

    async def search(self, query: str) -> DrugSearchResult:
        if self._search is not None:
            return self._search
        return DrugSearchResult(query=query, matches=[Drug(rxcui="1191", name="aspirin")])

    async def create(self, payload) -> Drug:
        return Drug(
            rxcui=payload.rxcui,
            name=payload.name,
            tty=payload.tty,
            ingredients=[Ingredient(rxcui=i.rxcui, name=i.name) for i in payload.ingredients],
        )

    async def list(self, limit: int = 50, offset: int = 0) -> DrugListResponse:
        return DrugListResponse(
            items=[Drug(rxcui="1", name="a")], total=1, limit=limit, offset=offset
        )

    async def delete(self, rxcui: str) -> bool:
        return self._deleted

    async def upsert_class(self, payload) -> None:
        return None


def _sample_pair() -> InteractionPair:
    return InteractionPair(
        rxcui_a="1191",
        rxcui_b="11289",
        name_a="aspirin",
        name_b="warfarin",
        severity=Severity.MAJOR,
        description="Bleeding risk.",
        source="seed",
    )


class FakeInteractionService:
    def __init__(self, *, deleted=True) -> None:
        self._deleted = deleted

    async def check(self, request) -> InteractionCheckResponse:
        return InteractionCheckResponse(
            checked_drugs=["aspirin", "warfarin"],
            unresolved=[],
            interactions=[_sample_pair()],
            highest_severity=Severity.MAJOR,
        )

    async def create_interaction(self, payload) -> InteractionPair:
        return InteractionPair(
            rxcui_a=payload.rxcui_a,
            rxcui_b=payload.rxcui_b,
            name_a="a",
            name_b="b",
            severity=payload.severity,
        )

    async def list_interactions(self, limit: int = 50, offset: int = 0) -> InteractionListResponse:
        return InteractionListResponse(items=[_sample_pair()], total=1, limit=limit, offset=offset)

    async def delete_interaction(self, rxcui_a: str, rxcui_b: str) -> bool:
        return self._deleted
