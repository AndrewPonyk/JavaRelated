# Fraud Detection Service

ML-powered fraud detection API for healthcare claims processing.

## Overview

This service provides real-time fraud scoring for healthcare claims using a heuristic-based scoring engine. It's designed to be easily extended with real ML models.

## Features

- **Real-time Scoring**: Score claims in milliseconds
- **Risk Factor Analysis**: Detailed breakdown of fraud indicators
- **Configurable Thresholds**: Adjustable risk levels
- **REST API**: OpenAPI-documented endpoints
- **Health Monitoring**: Built-in health checks and metrics

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/score` | Score a claim for fraud |
| GET | `/health` | Health check |
| GET | `/api/v1/metrics` | Service metrics |
| GET | `/api/v1/model/info` | Model information |
| GET | `/docs` | Swagger UI |

## Scoring Model

Current version uses heuristic rules:

| Factor | Description |
|--------|-------------|
| Amount Analysis | Flags high-value claims |
| Date Patterns | Weekend/holiday services |
| Procedure Codes | High-risk procedure detection |
| Diagnosis Codes | Vague/non-specific diagnoses |
| Claim Type | Risk by claim category |
| Round Amounts | Suspiciously round figures |

### Risk Levels

| Score Range | Level | Action |
|-------------|-------|--------|
| 0.0 - 0.3 | LOW | Auto-approve |
| 0.3 - 0.5 | MEDIUM | Flag for review |
| 0.5 - 0.7 | HIGH | Manual review required |
| 0.7 - 1.0 | CRITICAL | Deny / SIU referral |

## Running Locally

```bash
# Install dependencies
pip install -r requirements.txt

# Run the server
uvicorn app.main:app --host 0.0.0.0 --port 8090 --reload
```

## Docker

```bash
# Build image
docker build -t fraud-detection-service .

# Run container
docker run -p 8090:8090 fraud-detection-service
```

## Example Request

```bash
curl -X POST http://localhost:8090/api/v1/score \
  -H "Content-Type: application/json" \
  -d '{
    "claimId": "CLM-001",
    "claimType": "MEDICAL",
    "amount": 15000.00,
    "serviceDate": "2024-01-15",
    "patientId": "patient-123",
    "providerId": "provider-456",
    "diagnosisCodes": "M79.3",
    "procedureCodes": "99215"
  }'
```

## Example Response

```json
{
  "claimId": "CLM-001",
  "score": 0.45,
  "riskLevel": "MEDIUM",
  "riskFactors": [
    {
      "factor": "very_high_amount",
      "weight": 0.20,
      "description": "Very high claim amount ($15,000.00)"
    },
    {
      "factor": "vague_diagnosis_M79.3",
      "weight": 0.08,
      "description": "Non-specific diagnosis code: M79.3"
    }
  ],
  "recommendation": "REVIEW - Flag for secondary review",
  "modelVersion": "1.0.0"
}
```

## Extending with Real ML

To add a real ML model:

1. Train your model (scikit-learn, TensorFlow, etc.)
2. Save the model to `app/models/`
3. Update `scoring.py` to load and use the model
4. Update `MODEL_VERSION`

Example with scikit-learn:

```python
import joblib

class FraudScoringEngine:
    def __init__(self):
        self.model = joblib.load("app/models/fraud_model.pkl")

    def score(self, request):
        features = self._extract_features(request)
        score = self.model.predict_proba(features)[0][1]
        return score, self._explain(features)
```

## Configuration

Environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| PORT | 8090 | Server port |
| LOG_LEVEL | INFO | Logging level |
| MODEL_PATH | - | Path to ML model file |
