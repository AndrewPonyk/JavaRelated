# Infrastructure & GCP Setup

One-time setup for deploying the Image Classification Service to Google Cloud Run via
GitHub Actions using **Workload Identity Federation** (no service-account JSON keys).

## Prerequisites

- A GCP project (`$PROJECT_ID`) with billing enabled.
- APIs enabled: Cloud Run, Artifact Registry, Secret Manager, IAM Credentials,
  Cloud SQL Admin, Memorystore (Redis).

## 1. Artifact Registry

```bash
gcloud artifacts repositories create $AR_REPO \
  --repository-format=docker \
  --location=$REGION
```

## 2. Workload Identity Federation (keyless CI)

```bash
# Pool + provider
gcloud iam workload-identity-pools create github-pool --location=global
gcloud iam workload-identity-pools providers create-oidc github-provider \
  --location=global --workload-identity-pool=github-pool \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository"

# Deployer service account + role bindings
gcloud iam service-accounts create gh-deployer
# grant: roles/run.admin, roles/artifactregistry.writer, roles/iam.serviceAccountUser

# Allow the GitHub repo to impersonate the SA
gcloud iam service-accounts add-iam-policy-binding \
  gh-deployer@$PROJECT_ID.iam.gserviceaccount.com \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/attribute.repository/OWNER/REPO"
```

## 3. GitHub configuration

Set repo **Variables**: `GCP_PROJECT_ID`, `GCP_REGION`, `AR_REPO`.
Set repo **Secrets**: `WIF_PROVIDER`, `WIF_SERVICE_ACCOUNT`.
Create GitHub **Environments** `staging` and `production` (add a required reviewer to
`production` for the manual deploy gate).

## 4. Secrets (Secret Manager)

Store `DATABASE_URL`, `REDIS_URL`, `JWT_SECRET`, `API_KEYS` in Secret Manager and bind
them to the Cloud Run service as env vars.

## 5. Data services

- **Cloud SQL (Postgres)** for the taxonomy; connect via the Cloud SQL connector / VPC.
- **Memorystore (Redis)** for the cache; reachable over the same VPC connector.

> The model artifact (`*.onnx`, `labels.json`, `thresholds.json`) is either baked into
> the image (preferred for prod reproducibility) or pulled from GCS at startup.
