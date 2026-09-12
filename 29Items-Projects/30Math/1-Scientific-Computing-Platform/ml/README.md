# ml/ — SageMaker training & inference

Equation-pattern recognition: given an expression, predict its pattern class
(`linear`, `quadratic`, `trigonometric`, …) to power solver hints and
curriculum recommendations.

| Path | Purpose |
| --- | --- |
| `training/train.py` | SageMaker training entrypoint (`SM_*` contract); trains on a CSV channel or the synthetic corpus; runs locally too |
| `inference/inference.py` | Endpoint handlers: `model_fn` / `input_fn` / `predict_fn` / `output_fn` (feature-order skew guard) |
| `pipelines/sagemaker_pipeline.py` | SageMaker Pipeline: train → register (`PendingManualApproval`) |
| `pipelines/promote_endpoint.py` | Latest **Approved** package → serverless endpoint (blue/green `UpdateEndpoint`) |
| `data/` | Local scratch only — real datasets live in S3 (`s3://<bucket>/datasets/`) |

Train and exercise the full contract locally (no AWS needed):

```bash
PYTHONPATH=libs/sciengine/src python ml/training/train.py --model-dir /tmp/scp-model
# → {"metric": "validation:accuracy", "value": 1.0} on the synthetic corpus
```

Ground rules (docs/TECH-NOTES.md §3.6):

- **Feature parity**: features come from `sciengine.ml.features` — the same
  code the API fallback uses. Never re-implement featurization here.
- **No pickle across trust boundaries**: prefer `skops`/ONNX for artifacts.
- Pin `numpy`/`scikit-learn` in `requirements.txt` to match the app's `uv.lock`.

Run the pipeline (needs AWS credentials with the SageMaker execution role):

```bash
python ml/pipelines/sagemaker_pipeline.py --upsert          # define/update
python ml/pipelines/sagemaker_pipeline.py --upsert --execute  # and run
```

CI: `.github/workflows/ml-pipeline.yml` runs this weekly + on manual dispatch;
endpoint promotion is gated by the `ml-prod` GitHub environment.
