// 001 — Constraints & indexes for the Drug Interaction graph.
// Apply: cypher-shell -a "$NEO4J_URI" -u neo4j -p "$NEO4J_PASSWORD" -f 001_constraints_indexes.cypher

// --- Uniqueness constraints (also create backing indexes) ---
CREATE CONSTRAINT drug_rxcui IF NOT EXISTS
FOR (d:Drug) REQUIRE d.rxcui IS UNIQUE;

CREATE CONSTRAINT ingredient_rxcui IF NOT EXISTS
FOR (i:Ingredient) REQUIRE i.rxcui IS UNIQUE;

CREATE CONSTRAINT drugclass_id IF NOT EXISTS
FOR (c:DrugClass) REQUIRE c.classId IS UNIQUE;

// --- Lookup indexes ---
CREATE INDEX drug_name IF NOT EXISTS FOR (d:Drug) ON (d.name);
CREATE INDEX ingredient_name IF NOT EXISTS FOR (i:Ingredient) ON (i.name);

// --- Full-text index for drug name search ---
CREATE FULLTEXT INDEX drug_fulltext IF NOT EXISTS
FOR (d:Drug) ON EACH [d.name, d.synonym];
