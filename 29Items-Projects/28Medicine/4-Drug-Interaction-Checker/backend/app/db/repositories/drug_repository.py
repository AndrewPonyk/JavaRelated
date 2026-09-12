"""Cypher data access for Drug / Ingredient / DrugClass nodes."""

import re

from neo4j import AsyncDriver

from app.core.config import get_settings
from app.models.drug import DrugUpsert

_FULLTEXT_SANITIZE = re.compile(r"[^0-9A-Za-z ]+")


class DrugRepository:
    def __init__(self, driver: AsyncDriver) -> None:
        self._driver = driver
        self._database = get_settings().neo4j_database

    # ---- reads -------------------------------------------------------------
    async def get_drug(self, rxcui: str) -> dict | None:
        query = """
        MATCH (d:Drug {rxcui: $rxcui})
        OPTIONAL MATCH (d)-[:HAS_INGREDIENT]->(i:Ingredient)
        OPTIONAL MATCH (d)-[:MEMBER_OF]->(c:DrugClass)
        RETURN d.rxcui AS rxcui, d.name AS name, d.tty AS tty,
               collect(DISTINCT {rxcui: i.rxcui, name: i.name}) AS ingredients,
               collect(DISTINCT c.name) AS drug_classes
        """
        async with self._driver.session(database=self._database) as session:
            result = await session.run(query, rxcui=rxcui)
            record = await result.single()
            return dict(record) if record else None

    async def get_ingredient_rxcuis(self, drug_rxcui: str) -> list[str]:
        query = """
        MATCH (d:Drug {rxcui: $rxcui})-[:HAS_INGREDIENT]->(i:Ingredient)
        RETURN collect(i.rxcui) AS rxcuis
        """
        async with self._driver.session(database=self._database) as session:
            result = await session.run(query, rxcui=drug_rxcui)
            record = await result.single()
            return list(record["rxcuis"]) if record else []

    async def search_by_name(self, query_text: str, limit: int = 10) -> list[dict]:
        """Full-text drug search using the `drug_fulltext` index (prefix match)."""
        cleaned = _FULLTEXT_SANITIZE.sub(" ", query_text).strip()
        if not cleaned:
            return []
        lucene = " ".join(f"{token}*" for token in cleaned.split())
        query = """
        CALL db.index.fulltext.queryNodes('drug_fulltext', $q) YIELD node, score
        RETURN node.rxcui AS rxcui, node.name AS name, node.tty AS tty
        ORDER BY score DESC LIMIT $limit
        """
        async with self._driver.session(database=self._database) as session:
            result = await session.run(query, q=lucene, limit=limit)
            return [dict(record) async for record in result]

    async def list_drugs(self, limit: int = 50, offset: int = 0) -> tuple[list[dict], int]:
        data_query = """
        MATCH (d:Drug)
        RETURN d.rxcui AS rxcui, d.name AS name, d.tty AS tty
        ORDER BY d.name SKIP $offset LIMIT $limit
        """
        count_query = "MATCH (d:Drug) RETURN count(d) AS total"
        async with self._driver.session(database=self._database) as session:
            data = await session.run(data_query, offset=offset, limit=limit)
            items = [dict(record) async for record in data]
            count = await session.run(count_query)
            count_record = await count.single()
            total = count_record["total"] if count_record else 0
        return items, total

    # ---- writes ------------------------------------------------------------
    async def upsert_drug(self, rxcui: str, name: str, tty: str | None = None) -> None:
        query = """
        MERGE (d:Drug {rxcui: $rxcui})
        SET d.name = $name, d.tty = $tty, d.updatedAt = timestamp()
        """
        async with self._driver.session(database=self._database) as session:
            await session.run(query, rxcui=rxcui, name=name, tty=tty)

    async def upsert_class(self, class_id: str, name: str, class_type: str | None = None) -> None:
        query = """
        MERGE (c:DrugClass {classId: $class_id})
        SET c.name = $name, c.classType = $class_type
        """
        async with self._driver.session(database=self._database) as session:
            await session.run(query, class_id=class_id, name=name, class_type=class_type)

    async def upsert_drug_full(self, drug: DrugUpsert) -> None:
        """Idempotently upsert a drug with its ingredients and class links."""
        async with self._driver.session(database=self._database) as session:
            await session.run(
                "MERGE (d:Drug {rxcui: $rxcui}) "
                "SET d.name = $name, d.tty = $tty, d.updatedAt = timestamp()",
                rxcui=drug.rxcui,
                name=drug.name,
                tty=drug.tty,
            )
            for class_id in drug.class_ids:
                await session.run(
                    "MERGE (c:DrugClass {classId: $class_id}) "
                    "WITH c MATCH (d:Drug {rxcui: $rxcui}) MERGE (d)-[:MEMBER_OF]->(c)",
                    class_id=class_id,
                    rxcui=drug.rxcui,
                )
            for ing in drug.ingredients:
                await session.run(
                    "MERGE (i:Ingredient {rxcui: $rxcui}) SET i.name = $name",
                    rxcui=ing.rxcui,
                    name=ing.name,
                )
                await session.run(
                    "MATCH (d:Drug {rxcui: $drug}), (i:Ingredient {rxcui: $ing}) "
                    "MERGE (d)-[:HAS_INGREDIENT]->(i)",
                    drug=drug.rxcui,
                    ing=ing.rxcui,
                )
                for class_id in ing.class_ids:
                    await session.run(
                        "MERGE (c:DrugClass {classId: $class_id}) "
                        "WITH c MATCH (i:Ingredient {rxcui: $ing}) "
                        "MERGE (i)-[:BELONGS_TO_CLASS]->(c)",
                        class_id=class_id,
                        ing=ing.rxcui,
                    )

    async def delete_drug(self, rxcui: str) -> bool:
        query = """
        MATCH (d:Drug {rxcui: $rxcui})
        WITH d, count(d) AS found
        DETACH DELETE d
        RETURN found
        """
        async with self._driver.session(database=self._database) as session:
            result = await session.run(query, rxcui=rxcui)
            record = await result.single()
            return bool(record and record["found"])
