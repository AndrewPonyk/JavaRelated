"""Build the labeled training dataset from Neo4j.

Each row is an ingredient pair with graph features (X) and the curated
severity label (y).
"""
from __future__ import annotations

from dataclasses import dataclass

from neo4j import GraphDatabase

from training.graph_features import compute_features

# Curated interactions only (exclude anything the model itself wrote back).
_LABELED_PAIRS_QUERY = """
MATCH (a:Ingredient)-[r:INTERACTS_WITH]-(b:Ingredient)
WHERE a.rxcui < b.rxcui AND coalesce(r.source, '') <> 'ml-severity-model'
  AND r.severity IS NOT NULL
RETURN a.rxcui AS a, b.rxcui AS b, r.severity AS severity
"""


@dataclass
class LabeledPair:
    rxcui_a: str
    rxcui_b: str
    features: list[float]
    severity: str  # minor | moderate | major | contraindicated


def fetch_labeled_pairs(
    neo4j_uri: str, user: str = "neo4j", password: str = "password"
) -> list[LabeledPair]:
    """Pull curated interactions and compute their graph features."""
    driver = GraphDatabase.driver(neo4j_uri, auth=(user, password))
    pairs: list[LabeledPair] = []
    try:
        with driver.session() as session:
            rows = list(session.run(_LABELED_PAIRS_QUERY))
        for row in rows:
            features = compute_features(driver, row["a"], row["b"])
            pairs.append(
                LabeledPair(
                    rxcui_a=row["a"],
                    rxcui_b=row["b"],
                    features=features,
                    severity=row["severity"],
                )
            )
    finally:
        driver.close()
    return pairs
