# Learning to Rank assets

`featureset.json` defines the query-dependent and document features the ranking model
consumes. It is the **contract between training and serving** — `ml/ltr/train.py` must
compute feature values through this featureset (feature logging), and the runtime rescore
(`backend/app/search/ltr.py`) executes the model against the same features. Version the
featureset name (`products_features_v2`, …) whenever features change, and record which
featureset a model was trained on.

Two serving modes (`LTR_MODE` setting):

| | `plugin` | `native` |
|---|---|---|
| Mechanism | `sltr` query (community LTR plugin) | `learning_to_rank` rescorer (ES ≥ 8.12) |
| Works on Elastic Cloud | ❌ (plugins not installable) | ✅ |
| Upload path | `POST _ltr/_featureset/...` + model (see `ml/ltr/upload_model.py`) | eland `MLModel.import_ltr_model` |
| Model formats | RankLib / XGBoost JSON | XGBoost / LightGBM via eland |

Workflow: `ml/ltr/build_judgments.py` → `ml/ltr/train.py` → `ml/ltr/upload_model.py`,
then flip `LTR_MODE` (staging first — relevance regression suite must pass).
