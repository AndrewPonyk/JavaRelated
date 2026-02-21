terraform {
  required_version = ">= 1.0"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
  
  backend "s3" {
    bucket         = "loan-origination-terraform-state"
    key            = "infrastructure/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "terraform-state-lock"
  }
}

provider "aws" {
  region = var.aws_region
  
  default_tags {
    tags = {
      Project     = "LoanOriginationSystem"
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}

# NOTE: The child modules referenced below (./modules/vpc, ./modules/eks, ./modules/rds)
# are placeholders and do not exist yet. Create them before running terraform init/apply.
# Until then, running terraform will fail with "Module not found" errors.

module "vpc" {
  source = "./modules/vpc"

  environment         = var.environment
  vpc_cidr            = var.vpc_cidr
  availability_zones  = var.availability_zones
}

module "eks" {
  source = "./modules/eks"

  environment        = var.environment
  cluster_name       = "loan-origination-${var.environment}"
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  cluster_version    = "1.28"
}

module "rds" {
  source = "./modules/rds"

  environment        = var.environment
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  engine             = "oracle-ee"
  engine_version     = "19.0.0.0.ru-2023-07.rur-2023-07.r1"
  instance_class     = var.rds_instance_class
  allocated_storage  = 100
}

output "eks_cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "rds_endpoint" {
  value = module.rds.db_endpoint
}
