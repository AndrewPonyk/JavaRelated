"""Graph-derived feature extraction for severity prediction.

Features come from the 'drug property network': shared drug classes, graph
distance between ingredients, and precomputed node embeddings.
"""

from dataclasses import dataclass, field

from neo4j import AsyncDriver

from app.core.config import get_settings


@dataclass
class PairFeatures:
    rxcui_a: str
    rxcui_b: str
    shared_class_count: int = 0
    graph_distance: int = -1  # -1 == not connected within search depth
    embedding_cosine: float = 0.0
    extra: dict = field(default_factory=dict)

    def to_vector(self) -> list[float]:
        # NOTE: keep ordering in sync with the trained model's feature schema.
        return [
            float(self.shared_class_count),
            float(self.graph_distance),
            float(self.embedding_cosine),
        ]


class FeatureExtractor:
    def __init__(self, driver: AsyncDriver) -> None:
        self._driver = driver
        self._database = get_settings().neo4j_database

    async def extract(self, rxcui_a: str, rxcui_b: str) -> PairFeatures:
        # Distance is measured over the *drug-property network* (class edges only),
        # deliberately excluding INTERACTS_WITH so the same feature is leakage-free
        # at training time (the target edge would otherwise make distance == 1).
        query = """
        MATCH (a:Ingredient {rxcui: $a}), (b:Ingredient {rxcui: $b})
        OPTIONAL MATCH (a)-[:BELONGS_TO_CLASS]->(c:DrugClass)<-[:BELONGS_TO_CLASS]-(b)
        WITH a, b, count(DISTINCT c) AS shared_classes
        OPTIONAL MATCH p = shortestPath((a)-[:BELONGS_TO_CLASS*..4]-(b))
        RETURN shared_classes AS shared_class_count,
               CASE WHEN p IS NULL THEN -1 ELSE length(p) END AS graph_distance,
               a.embedding AS emb_a, b.embedding AS emb_b
        """
        async with self._driver.session(database=self._database) as session:
            result = await session.run(query, a=rxcui_a, b=rxcui_b)
            record = await result.single()

        if record is None:
            return PairFeatures(rxcui_a=rxcui_a, rxcui_b=rxcui_b)

        return PairFeatures(
            rxcui_a=rxcui_a,
            rxcui_b=rxcui_b,
            shared_class_count=record["shared_class_count"],
            graph_distance=record["graph_distance"],
            embedding_cosine=_cosine(record.get("emb_a"), record.get("emb_b")),
        )


def _cosine(a: list[float] | None, b: list[float] | None) -> float:
    if not a or not b or len(a) != len(b):
        return 0.0
    dot = sum(x * y for x, y in zip(a, b, strict=False))
    norm_a = sum(x * x for x in a) ** 0.5
    norm_b = sum(y * y for y in b) ** 0.5
    return dot / (norm_a * norm_b) if norm_a and norm_b else 0.0
