# Log Analytics Platform — AWS infrastructure.
# Apply per environment:  terraform apply -var-file=envs/dev.tfvars

terraform {
  required_version = ">= 1.7"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # TODO: per-env remote state before anyone else touches this.
  # backend "s3" {
  #   bucket         = "la-terraform-state-<account-id>"
  #   key            = "log-analytics/terraform.tfstate"
  #   region         = "eu-central-1"
  #   dynamodb_table = "la-terraform-locks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = "log-analytics-platform"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

locals {
  name_prefix = "log-analytics-${var.environment}"
}
