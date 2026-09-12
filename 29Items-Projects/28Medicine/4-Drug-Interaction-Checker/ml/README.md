# ML — Severity Prediction (offline training)

Trains the drug-interaction **severity** model used by the online ML service
(`backend/app/ml`). This is a batch/CI concern that produces a versioned
artifact in S3; the API/ML services only *consume* that artifact.

## Pipeline

```
Neo4j (labeled pairs)
      │  dataset.py
      ▼
Graph features  ──  graph_features.py  (shared classes, distance, embeddings)
      │
      ▼
Train + evaluate (train.py)  ──►  artifact  ──►  s3://dic-models/severity/<date>
```

## Run

```bash
pip install -r requirements.txt

python -m training.train \
  --neo4j-uri bolt://localhost:7687 \
  --output s3://dic-models/severity/$(date +%Y%m%d)
```

## Notes

- **Labels** come from curated `INTERACTS_WITH.severity` values in the graph.
- **Node embeddings** (node2vec / GraphSAGE via Neo4j GDS) are computed offline
  and materialized onto `Ingredient.embedding` for cheap request-time features.
- **Evaluation:** report macro-F1 and PR-AUC per class; watch class imbalance
  (contraindicated pairs are rare).
- **Feature schema** must stay in sync with `backend/app/ml/features.py:PairFeatures.to_vector`.
- Promote a new artifact only after offline metrics clear the gate; the online
  service pins `ML_MODEL_ARTIFACT_URI`.
