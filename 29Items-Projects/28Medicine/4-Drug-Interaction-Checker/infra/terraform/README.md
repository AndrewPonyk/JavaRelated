# Terraform — AWS Platform (skeleton)

Provisions the cloud foundation for the Drug Interaction Checker:

- **ECR** repositories (`dic-backend`, `dic-frontend`) — immutable tags, scan-on-push
- **S3** bucket for ML model artifacts (versioned)
- **EKS** cluster + managed node group *(commented module in `eks.tf`)*
- *(TODO)* VPC, IRSA roles, AWS Load Balancer Controller, External Secrets

> This is intentionally a **skeleton**. Configure remote state, pin module
> versions, and review IAM before `apply`.

## Usage

```bash
cd infra/terraform
terraform init
terraform plan  -var="environment=dev"
terraform apply -var="environment=dev"
```

## Conventions

- One state per environment (workspaces or separate backends).
- Least-privilege IAM; pods use **IRSA**, never node instance credentials.
- CI authenticates via **GitHub OIDC** (no long-lived keys).
