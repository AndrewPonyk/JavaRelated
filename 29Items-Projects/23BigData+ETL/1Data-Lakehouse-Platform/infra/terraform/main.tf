# Data Lakehouse Platform — root module.
# One state per environment; the backend is configured at init time:
#   terraform init -backend-config="bucket=<state-bucket>" \
#                  -backend-config="key=lakehouse/${env}.tfstate" \
#                  -backend-config="region=eu-central-1"
#   terraform apply -var-file=envs/dev.tfvars
#
# Scope: this stack provisions the data plane (lake storage, streaming,
# processing). Serving-tier infrastructure (MWAA, Trino cluster, ECS API,
# CloudFront console) is tracked as Phase 3 in docs/PROJECT-PLAN.md.

terraform {
  required_version = ">= 1.7"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
  }

  backend "s3" {
    # Partial configuration — supplied via -backend-config (see header).
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

module "lake_storage" {
  source      = "./modules/s3_lake"
  project     = var.project
  environment = var.environment
  kms_key_arn = var.kms_key_arn
}

module "streaming" {
  source                    = "./modules/msk"
  project                   = var.project
  environment               = var.environment
  vpc_id                    = var.vpc_id
  private_subnet_ids        = var.private_subnet_ids
  client_security_group_ids = var.kafka_client_security_group_ids
}

module "processing" {
  source      = "./modules/emr_serverless"
  project     = var.project
  environment = var.environment
  lake_bucket_arns = [
    module.lake_storage.bucket_arns["bronze"],
    module.lake_storage.bucket_arns["silver"],
    module.lake_storage.bucket_arns["gold"],
  ]
  artifacts_bucket_arn = module.lake_storage.bucket_arns["artifacts"]
}
