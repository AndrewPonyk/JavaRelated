# Root Terraform configuration for the Drug Interaction Checker platform.
# This is a SKELETON: fill in backend state, networking, and module versions
# before applying.

terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # TODO: configure remote state (S3 + DynamoDB lock).
  # backend "s3" {
  #   bucket         = "dic-tfstate"
  #   key            = "platform/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "dic-tflock"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region
  default_tags {
    tags = {
      Project     = "drug-interaction-checker"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

# ECR repositories for the application images.
resource "aws_ecr_repository" "backend" {
  name                 = "dic-backend"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_repository" "frontend" {
  name                 = "dic-frontend"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration {
    scan_on_push = true
  }
}

# S3 bucket for ML model artifacts.
resource "aws_s3_bucket" "models" {
  bucket = var.model_artifact_bucket
}

resource "aws_s3_bucket_versioning" "models" {
  bucket = aws_s3_bucket.models.id
  versioning_configuration {
    status = "Enabled"
  }
}

# TODO: VPC module, EKS module (see eks.tf), IRSA roles, External Secrets,
# AWS Load Balancer Controller, Neo4j (AuraDB or self-managed), ElastiCache.
