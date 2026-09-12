"""Cypher data access for INTERACTS_WITH relationships.

Interactions are stored once between Ingredient nodes and queried undirected;
the ``a.rxcui < b.rxcui`` filter emits each pair exactly once.
"""

from neo4j import AsyncDriver

from app.core.config import get_settings

_RETURN_CLAUSE = """
    RETURN a.rxcui AS rxcui_a, a.name AS name_a,
           b.rxcui AS rxcui_b, b.name AS name_b,
           r.severity AS severity, r.mechanism AS mechanism,
           r.evidenceLevel AS evidence_level, r.description AS description,
           r.source AS source
"""


class InteractionRepository:
    def __init__(self, driver: AsyncDriver) -> None:
        self._driver = driver
        self._database = get_settings().neo4j_database

    async def find_interaction(self, rxcui_a: str, rxcui_b: str) -> dict | None:
        query = (
            "MATCH (a:Ingredient)-[r:INTERACTS_WITH]-(b:Ingredient) "
            "WHERE a.rxcui = $a AND b.rxcui = $b " + _RETURN_CLAUSE + " LIMIT 1"
        )
        async with self._driver.session(database=self._database) as session:
            result = await session.run(query, a=rxcui_a, b=rxcui_b)
            record = await result.single()
            return dict(record) if record else None

    async def find_interactions_for_set(self, rxcuis: list[str]) -> list[dict]:
        """Return all known interactions among a set of ingredient RxCUIs."""
        if len(rxcuis) < 2:
            return []
        query = (
            "MATCH (a:Ingredient)-[r:INTERACTS_WITH]-(b:Ingredient) "
            "WHERE a.rxcui IN $rxcuis AND b.rxcui IN $rxcuis AND a.rxcui < b.rxcui "
            + _RETURN_CLAUSE
        )
        async with self._driver.session(database=self._database) as session:
            result = await session.run(query, rxcuis=rxcuis)
            return [dict(record) async for record in result]

    async def list_interactions(self, limit: int = 50, offset: int = 0) -> tuple[list[dict], int]:
        data_query = (
            "MATCH (a:Ingredient)-[r:INTERACTS_WITH]-(b:Ingredient) "
            "WHERE a.rxcui < b.rxcui "
            + _RETURN_CLAUSE
            + " ORDER BY r.severity DESC, a.name SKIP $offset LIMIT $limit"
        )
        count_query = (
            "MATCH (a:Ingredient)-[r:INTERACTS_WITH]-(b:Ingredient) "
            "WHERE a.rxcui < b.rxcui RETURN count(r) AS total"
        )
        async with self._driver.session(database=self._database) as session:
            data = await session.run(data_query, offset=offset, limit=limit)
            items = [dict(record) async for record in data]
            count = await session.run(count_query)
            count_record = await count.single()
            total = count_record["total"] if count_record else 0
        return items, total

    async def upsert_interaction(
        self,
        rxcui_a: str,
        rxcui_b: str,
        *,
        severity: str,
        mechanism: str | None = None,
        evidence_level: str | None = None,
        description: str | None = None,
        source: str | None = None,
    ) -> bool:
        """Create/update an interaction. Returns False if an ingredient is missing."""
        lo, hi = sorted([rxcui_a, rxcui_b])
        query = """
        MATCH (a:Ingredient {rxcui: $lo}), (b:Ingredient {rxcui: $hi})
        MERGE (a)-[r:INTERACTS_WITH]->(b)
        SET r.severity = $severity, r.mechanism = $mechanism,
            r.evidenceLevel = $evidence_level, r.description = $description,
            r.source = $source, r.updatedAt = timestamp()
        RETURN count(r) AS written
        """
        async with self._driver.session(database=self._database) as session:
            result = await session.run(
                query,
                lo=lo,
                hi=hi,
                severity=severity,
                mechanism=mechanism,
                evidence_level=evidence_level,
                description=description,
                source=source,
            )
            record = await result.single()
            return bool(record and record["written"])

    async def delete_interaction(self, rxcui_a: str, rxcui_b: str) -> bool:
        query = """
        MATCH (a:Ingredient)-[r:INTERACTS_WITH]-(b:Ingredient)
        WHERE a.rxcui = $a AND b.rxcui = $b
        WITH r, count(r) AS found
        DELETE r
        RETURN found
        """
        async with self._driver.session(database=self._database) as session:
            result = await session.run(query, a=rxcui_a, b=rxcui_b)
            record = await result.single()
            return bool(record and record["found"])
