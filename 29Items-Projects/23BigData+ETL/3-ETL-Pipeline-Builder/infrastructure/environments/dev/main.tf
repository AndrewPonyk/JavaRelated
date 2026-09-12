# Environment root: dev. State is isolated per environment.
# Bootstrap once by hand: the tfstate bucket + DynamoDB lock table.

terraform {
  required_version = ">= 1.7.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }

  backend "s3" {
    # Supplied via -backend-config (CI) or a backend.hcl file:
    #   bucket         = "etl-pipeline-builder-tfstate"
    #   key            = "dev/terraform.tfstate"
    #   region         = "eu-central-1"
    #   dynamodb_table = "terraform-locks"
    #   encrypt        = true
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = local.tags
  }
}

locals {
  name = "${var.project}-${var.env}"
  tags = {
    Project     = var.project
    Environment = var.env
    ManagedBy   = "terraform"
  }
}

module "networking" {
  source = "../../modules/networking"

  name                 = local.name
  region               = var.region
  vpc_cidr             = var.vpc_cidr
  azs                  = var.azs
  private_subnet_cidrs = var.private_subnet_cidrs
  tags                 = local.tags
}

module "msk" {
  source = "../../modules/msk"

  name               = local.name
  vpc_id             = module.networking.vpc_id
  subnet_ids         = module.networking.private_subnet_ids
  client_cidr        = module.networking.vpc_cidr
  broker_count       = var.msk_broker_count
  instance_type      = var.msk_instance_type
  ebs_volume_size_gb = var.msk_ebs_gb
  tags               = local.tags
}

module "data_lake" {
  source = "../../modules/data_lake"

  name = local.name
  env  = var.env
  tags = local.tags
}

module "orchestration" {
  source = "../../modules/orchestration"

  name       = local.name
  enabled    = var.mwaa_enabled
  subnet_ids = module.networking.private_subnet_ids
  tags       = local.tags
}

module "realtime" {
  source = "../../modules/realtime"

  name        = local.name
  vpc_id      = module.networking.vpc_id
  subnet_ids  = module.networking.private_subnet_ids
  client_cidr = module.networking.vpc_cidr
  tags        = local.tags
}

output "kafka_bootstrap" {
  value = module.msk.bootstrap_brokers_sasl_iam
}

output "redis_endpoint" {
  value = module.realtime.redis_primary_endpoint
}

output "lake_bucket" {
  value = module.data_lake.lake_bucket
}

output "mwaa_dags_bucket" {
  value = module.orchestration.dags_bucket
}

output "ecr_repositories" {
  value = module.realtime.ecr_repository_urls
}
