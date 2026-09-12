# Root Terraform for the ML platform's AWS footprint.
# Skeleton only — fill in module sources and variables per environment.
# State is remote (S3 + DynamoDB lock); never commit *.tfstate.

terraform {
  required_version = ">= 1.7"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # TODO: configure remote backend per environment.
  # backend "s3" {
  #   bucket         = "emlp-terraform-state"
  #   key            = "platform/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "emlp-terraform-locks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region
  default_tags {
    tags = {
      Project     = "enterprise-ml-platform"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

# --- Networking -------------------------------------------------------------
# module "vpc" { source = "terraform-aws-modules/vpc/aws" ... }  # TODO

# --- EKS (Kubeflow + platform services + GPU node pools via Karpenter) -------
# module "eks" { source = "terraform-aws-modules/eks/aws" ... }   # TODO

# --- RDS Postgres (platform metadata + MLflow backend store) ----------------
# module "rds" { ... }                                            # TODO

# --- S3 buckets (artifacts, datasets) + KMS ---------------------------------
# resource "aws_s3_bucket" "artifacts" { ... }                   # TODO

# --- IAM: SageMaker execution role + IRSA for in-cluster services ------------
# resource "aws_iam_role" "sagemaker_execution" { ... }          # TODO

# --- ECR repositories for backend/frontend/serving images -------------------
# resource "aws_ecr_repository" "backend" { ... }                # TODO
