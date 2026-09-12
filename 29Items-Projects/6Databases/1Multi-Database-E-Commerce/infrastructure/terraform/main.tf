# =====================================================================
# ShopFlow — AWS infrastructure (skeleton).
# Provisions the platform substrate: network, EKS, container registries,
# and the managed data services that back each microservice.
# Apply per-environment via workspaces or -var-file=envs/<env>.tfvars.
# =====================================================================
terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
  }
  # Remote state (create the bucket/table out-of-band or in a bootstrap module).
  backend "s3" {
    bucket         = "shopflow-tfstate"
    key            = "platform/terraform.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "shopflow-tflock"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region
  default_tags {
    tags = {
      Project     = "shopflow"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

# ---- Network -------------------------------------------------------------
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.8"

  name = "${var.project}-${var.environment}"
  cidr = var.vpc_cidr

  azs             = var.availability_zones
  private_subnets = var.private_subnets
  public_subnets  = var.public_subnets

  enable_nat_gateway = true
  single_nat_gateway = var.environment != "prod"
}

# ---- EKS cluster ---------------------------------------------------------
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.8"

  cluster_name    = var.eks_cluster_name
  cluster_version = var.eks_version

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  enable_irsa = true # IAM Roles for Service Accounts (pod-level AWS perms)

  eks_managed_node_groups = {
    general = {
      instance_types = var.node_instance_types
      min_size       = var.node_min_size
      max_size       = var.node_max_size
      desired_size   = var.node_desired_size
    }
  }
}

# ---- Container registries (one per service) ------------------------------
resource "aws_ecr_repository" "service" {
  for_each             = toset(var.services)
  name                 = "${var.project}/${each.value}"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration {
    scan_on_push = true
  }
}

# ---- Managed data services (stubs — flesh out per environment) -----------
# MSK (Kafka), ElastiCache (Redis), OpenSearch, and RDS/Oracle live in their
# own modules so they can scale and be replaced independently of compute.
# TODO: module "msk" { ... }          # event backbone
# TODO: module "elasticache_redis"    # sessions/cache
# TODO: module "opensearch"           # search-service
# TODO: module "rds_oracle"           # order-service (or self-managed/Atlas/Aura)
