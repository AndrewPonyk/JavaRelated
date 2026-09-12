"""Offline graph feature computation.

Mirrors the online extractor (backend/app/ml/features.py) so training and
serving see the same feature schema:

    [shared_class_count, graph_distance, embedding_cosine]

Distance is measured over the drug-property network (BELONGS_TO_CLASS only) to
stay leakage-free w.r.t. the INTERACTS_WITH target edge.
"""
from __future__ import annotations

from neo4j import Driver

# Must match backend/app/ml/model.py:_CLASS_TO_SEVERITY ordering.
SEVERITY_CLASSES = ["minor", "moderate", "major", "contraindicated"]

_FEATURE_QUERY = """
MATCH (a:Ingredient {rxcui: $a}), (b:Ingredient {rxcui: $b})
OPTIONAL MATCH (a)-[:BELONGS_TO_CLASS]->(c:DrugClass)<-[:BELONGS_TO_CLASS]-(b)
WITH a, b, count(DISTINCT c) AS shared
OPTIONAL MATCH p = shortestPath((a)-[:BELONGS_TO_CLASS*..4]-(b))
RETURN shared AS shared_class_count,
       CASE WHEN p IS NULL THEN -1 ELSE length(p) END AS graph_distance,
       a.embedding AS emb_a, b.embedding AS emb_b
"""


def cosine(a: list[float] | None, b: list[float] | None) -> float:
    if not a or not b or len(a) != len(b):
        return 0.0
    dot = sum(x * y for x, y in zip(a, b))
    na = sum(x * x for x in a) ** 0.5
    nb = sum(y * y for y in b) ** 0.5
    return dot / (na * nb) if na and nb else 0.0


def compute_features(driver: Driver, rxcui_a: str, rxcui_b: str) -> list[float]:
    """Return the feature vector for an ingredient pair."""
    with driver.session() as session:
        record = session.run(_FEATURE_QUERY, a=rxcui_a, b=rxcui_b).single()
    if record is None:
        return [0.0, -1.0, 0.0]
    return [
        float(record["shared_class_count"]),
        float(record["graph_distance"]),
        cosine(record.get("emb_a"), record.get("emb_b")),
    ]


def compute_node_embeddings(driver: Driver, dimensions: int = 64) -> None:
    """Compute + persist node embeddings via Neo4j GDS (fastRP).

    Requires the Graph Data Science plugin (bundled in docker-compose). Safe to
    skip: if GDS is unavailable the rule-based features simply use cosine 0.
    """
    project = """
    CALL gds.graph.project(
      'ingredient-net',
      ['Ingredient', 'DrugClass'],
      {BELONGS_TO_CLASS: {orientation: 'UNDIRECTED'}}
    )
    """
    embed = """
    CALL gds.fastRP.write('ingredient-net', {
      embeddingDimension: $dim,
      writeProperty: 'embedding'
    })
    """
    with driver.session() as session:
        session.run("CALL gds.graph.drop('ingredient-net', false)")
        session.run(project)
        session.run(embed, dim=dimensions)
        session.run("CALL gds.graph.drop('ingredient-net', false)")
