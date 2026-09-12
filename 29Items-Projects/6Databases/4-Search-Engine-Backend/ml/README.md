# ML — Learning to Rank pipeline

Offline loop (never in the serving path):

```
search_events (PG) ──> build_judgments.py ──> judgments.tsv
                                                  │
featureset.json (ES feature logging) ──> train.py ──> model artifact
                                                  │
                                        upload_model.py ──> ES model store
```

Install deps: `pip install -e "backend[ml]"` (xgboost, pandas, eland).

Run order:
1. `python ml/ltr/build_judgments.py --days 30`
2. `python ml/ltr/train.py --judgments judgments.tsv`
3. `python ml/ltr/upload_model.py --model model.json --mode plugin|native`

Guardrails: keep the BM25 fallback (`LTR_MODE=off`) deployable at all times; gate model
promotion on the relevance regression suite (nDCG@10 on the golden query set) and roll
out behind a staging → prod flag flip. Log the featureset version with every model.
