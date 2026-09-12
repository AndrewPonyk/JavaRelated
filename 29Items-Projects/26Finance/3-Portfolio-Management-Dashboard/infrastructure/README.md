# Infrastructure

Target platform: **AWS ECS (Fargate)**. This folder holds deployment artifacts
that the GitHub Actions pipeline (`.github/workflows/deploy.yml`) consumes.

## Components

| AWS service | Purpose |
| --- | --- |
| **ECR** | Container registry for `portfolio-api` and `portfolio-web` images. |
| **ECS (Fargate)** | Runs `api` (behind ALB), `worker` (Celery), and an optional `beat` scheduler — all off the backend image. |
| **ALB** | TLS termination + routing: `/api/*` → API target group, `/*` → web. |
| **RDS (PostgreSQL)** | Primary datastore; encrypted at rest, `sslmode=require`. |
| **ElastiCache (Redis)** | Celery broker/result backend + result cache. |
| **Secrets Manager** | `DATABASE_URL`, `SECRET_KEY`, `REDIS_URL`, market-data keys. |
| **CloudWatch** | Log groups (`/ecs/portfolio-*`) + autoscaling alarms. |

## Files

- `ecs/task-definition.json` — Fargate task definition template for the API.
  Replace `<ACCOUNT_ID>` / `<REGION>` and the image tag during deploy. The
  pipeline renders the image via `aws-actions/amazon-ecs-render-task-definition`.

## Deploy flow (see deploy.yml)

1. Build + tag image with the git SHA, push to ECR.
2. Run `alembic upgrade head` as a one-off ECS task (gated, before traffic shift).
3. Render the task definition with the new image, deploy, wait for stability.

## Recommended next step

Promote this to real IaC — **Terraform** or **AWS CDK** — covering the VPC, ALB,
ECS services + autoscaling policies, RDS, ElastiCache, and IAM roles. The JSON
here is the minimal artifact the CD pipeline needs in the meantime.

> Worker autoscaling should track **Redis queue depth** (custom CloudWatch
> metric), while the API scales on CPU + ALB request count.
