# Terraform IaC — STUB / skeleton.
# Provisions the AWS footprint for the platform. Fill in modules incrementally (Phase 2).
#
#   terraform init && terraform plan -var-file=envs/dev.tfvars

terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
  # backend "s3" { ... }   # TODO: remote state (S3 + DynamoDB lock)
}

provider "aws" {
  region = var.aws_region
}

variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "environment" {
  type    = string
  default = "dev"
}

# ── Resources to define (TODO) ──────────────────────────
# - VPC + public/private subnets (module "vpc")
# - ECR repository for vsp-backend
# - ECS cluster + Fargate service + task definition (renders infra/ecs/task-definition.json)
# - Application Load Balancer + target group (health check: /api/v1/health/ready)
# - RDS PostgreSQL (with pgvector) + parameter group
# - ElastiCache Redis
# - IAM roles: ecs-execution-role, ecs-task-role
# - Secrets Manager entries: DATABASE_URL, REDIS_URL, API_KEY, PINECONE_API_KEY
# - CloudWatch log group /ecs/vsp-backend
# - Autoscaling target-tracking policies (CPU + ALB request count)

output "next_steps" {
  value = "Define VPC, ECR, ECS, ALB, RDS, ElastiCache modules for environment: ${var.environment}"
}
