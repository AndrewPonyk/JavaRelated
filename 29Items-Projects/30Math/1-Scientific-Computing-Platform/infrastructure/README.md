# infrastructure/

Infrastructure-as-code home. Phase 1 runs on docker-compose locally; the AWS
footprint (Phase 2) is:

- **ECS Fargate**: `scp-api` + `scp-worker` services behind an ALB
- **RDS PostgreSQL**, **ElastiCache Redis**
- **S3 + CloudFront** for the SPA and computation artifacts
- **SageMaker**: pipeline, model registry, serverless endpoint, Studio domain
- **ECR** repositories: `scp-backend`, `scp-frontend`, `scp-jupyter-kernel`

TODO(phase-2): Terraform modules per environment (`envs/dev`, `envs/staging`,
`envs/prod`) with remote state in S3 + DynamoDB locking. Secrets are **not**
IaC-managed values — only Secrets Manager *references* appear here
(docs/ARCHITECTURE.md §2.5).

`sagemaker/endpoint-config.example.json` documents the serverless endpoint
shape consumed by the promotion script in `ml-pipeline.yml`.
