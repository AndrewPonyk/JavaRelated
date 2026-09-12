# ML Inference Service — Chest X-ray Classification

Diagnostic-assist microservice. Classifies chest radiographs (modalities `CR`/`DX`)
into the 14 ChestX-ray14 pathology labels (CheXNet / DenseNet-121 lineage).

> ⚠️ **Decision support only.** Outputs assist a radiologist; they are not a
> diagnosis. Every prediction is stored with the model name + version. Clinical
> deployment requires the appropriate regulatory posture (FDA 510(k) / CE-MDR).

## Layout
- `inference/chest_xray_classifier.py` — model wrapper (load + preprocess + predict).
- `inference/server.py` — FastAPI service exposing `POST /v1/predict`.
- `models/` — weights pulled from S3 (`ML_MODEL_S3_URI`) at deploy time; not committed.

## Run locally
```bash
pip install -r requirements.txt
uvicorn inference.server:app --port 8001
```

## Contract
`POST /v1/predict` → `{ sop_instance_uid, object_key }` →
`{ predictions: {label: prob}, top_label, top_score, model_name, model_version }`.

## Notes
- Preprocessing **must** match training (resize 224, ImageNet normalisation,
  MONOCHROME1 inversion + VOI LUT). Version preprocessing with the weights.
- GPU: swap the Dockerfile base to a CUDA runtime and install the matching torch.
