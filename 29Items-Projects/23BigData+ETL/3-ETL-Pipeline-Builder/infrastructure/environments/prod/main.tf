# Environment root: prod — same module graph, production sizing.
# Apply only via infra.yml with the "production" GitHub environment approval.

terraform {
  required_version = ">= 1.7.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }

  backend "s3" {
    # -backend-config: key = "prod/terraform.tfstate" (same bucket/lock table)
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
  source               = "../../modules/networking"
  name                 = local.name
  region               = var.region
  vpc_cidr             = var.vpc_cidr
  azs                  = var.azs
  private_subnet_cidrs = var.private_subnet_cidrs
  tags                 = local.tags
}

module "msk" {
  source             = "../../modules/msk"
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
  name   = local.name
  env    = var.env
  tags   = local.tags
}

module "orchestration" {
  source            = "../../modules/orchestration"
  name              = local.name
  enabled           = true
  environment_class = "mw1.medium"
  subnet_ids        = module.networking.private_subnet_ids
  tags              = local.tags
}

module "realtime" {
  source          = "../../modules/realtime"
  name            = local.name
  vpc_id          = module.networking.vpc_id
  subnet_ids      = module.networking.private_subnet_ids
  client_cidr     = module.networking.vpc_cidr
  redis_node_type = "cache.m7g.large"
  redis_replicas  = 1 # multi-AZ hot store
  tags            = local.tags
}

variable "project" {
  type    = string
  default = "etl-pipeline-builder"
}

variable "env" {
  type    = string
  default = "prod"
}

variable "region" {
  type    = string
  default = "eu-central-1"
}

variable "vpc_cidr" {
  type    = string
  default = "10.22.0.0/16"
}

variable "azs" {
  type    = list(string)
  default = ["eu-central-1a", "eu-central-1b", "eu-central-1c"]
}

variable "private_subnet_cidrs" {
  type    = list(string)
  default = ["10.22.1.0/24", "10.22.2.0/24", "10.22.3.0/24"]
}

variable "msk_broker_count" {
  type    = number
  default = 3
}

variable "msk_instance_type" {
  type    = string
  default = "kafka.m7g.large"
}

variable "msk_ebs_gb" {
  type    = number
  default = 1000
}
