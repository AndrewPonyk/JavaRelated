// 002 — Minimal seed data for local development / smoke tests.
// NOT for production: production interactions come from a curated, sourced dataset.
//
// Each statement is SELF-CONTAINED (re-MATCHes nodes by rxcui/classId) because
// Cypher variables do not persist across ';'-separated statements.

// --- Ingredients (RxNorm IN-level RxCUIs) ---
MERGE (i:Ingredient {rxcui: "11289"}) SET i.name = "warfarin";
MERGE (i:Ingredient {rxcui: "1191"})  SET i.name = "aspirin";
MERGE (i:Ingredient {rxcui: "5640"})  SET i.name = "ibuprofen";
MERGE (i:Ingredient {rxcui: "36567"}) SET i.name = "simvastatin";
MERGE (i:Ingredient {rxcui: "21212"}) SET i.name = "clarithromycin";

// --- Drug classes (ATC) ---
MERGE (c:DrugClass {classId: "ATC_B01AA"}) SET c.name = "Vitamin K antagonists", c.classType = "ATC";
MERGE (c:DrugClass {classId: "ATC_M01AE"}) SET c.name = "NSAIDs, propionic acid derivatives", c.classType = "ATC";
MERGE (c:DrugClass {classId: "ATC_C10AA"}) SET c.name = "HMG-CoA reductase inhibitors", c.classType = "ATC";
MERGE (c:DrugClass {classId: "ATC_J01FA"}) SET c.name = "Macrolides", c.classType = "ATC";

// --- Class membership ---
MATCH (i:Ingredient {rxcui: "11289"}), (c:DrugClass {classId: "ATC_B01AA"}) MERGE (i)-[:BELONGS_TO_CLASS]->(c);
MATCH (i:Ingredient {rxcui: "5640"}),  (c:DrugClass {classId: "ATC_M01AE"}) MERGE (i)-[:BELONGS_TO_CLASS]->(c);
MATCH (i:Ingredient {rxcui: "36567"}), (c:DrugClass {classId: "ATC_C10AA"}) MERGE (i)-[:BELONGS_TO_CLASS]->(c);
MATCH (i:Ingredient {rxcui: "21212"}), (c:DrugClass {classId: "ATC_J01FA"}) MERGE (i)-[:BELONGS_TO_CLASS]->(c);

// --- Curated interactions (matched undirected at query time) ---
MATCH (a:Ingredient {rxcui: "1191"}), (b:Ingredient {rxcui: "11289"})
MERGE (a)-[r:INTERACTS_WITH]->(b)
SET r.severity = "major",
    r.mechanism = "Additive bleeding risk; aspirin inhibits platelet aggregation.",
    r.evidenceLevel = "established",
    r.description = "Concurrent use markedly increases risk of serious bleeding.",
    r.source = "seed";

MATCH (a:Ingredient {rxcui: "5640"}), (b:Ingredient {rxcui: "11289"})
MERGE (a)-[r:INTERACTS_WITH]->(b)
SET r.severity = "moderate",
    r.mechanism = "NSAID GI mucosal injury plus anticoagulation.",
    r.evidenceLevel = "study",
    r.description = "Increased bleeding risk; monitor INR.",
    r.source = "seed";

MATCH (a:Ingredient {rxcui: "21212"}), (b:Ingredient {rxcui: "36567"})
MERGE (a)-[r:INTERACTS_WITH]->(b)
SET r.severity = "contraindicated",
    r.mechanism = "CYP3A4 inhibition raises simvastatin levels -> rhabdomyolysis risk.",
    r.evidenceLevel = "established",
    r.description = "Avoid combination; risk of severe myopathy/rhabdomyolysis.",
    r.source = "seed";
