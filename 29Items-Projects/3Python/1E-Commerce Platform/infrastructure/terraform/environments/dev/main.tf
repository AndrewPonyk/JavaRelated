# Terraform configuration for Development Environment
# E-Commerce Platform

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # TODO: Configure remote state backend
  # backend "s3" {
  #   bucket         = "ecommerce-terraform-state"
  #   key            = "dev/terraform.tfstate"
  #   region         = "us-east-1"
  #   encrypt        = true
  #   dynamodb_table = "terraform-locks"
  # }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "ecommerce"
      Environment = "dev"
      ManagedBy   = "terraform"
    }
  }
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

# TODO: Define VPC, subnets, and networking

# TODO: Import and configure modules
# module "ecs" {
#   source = "../../modules/ecs"
#
#   environment        = "dev"
#   vpc_id             = module.networking.vpc_id
#   private_subnet_ids = module.networking.private_subnet_ids
#   backend_image      = var.backend_image
#   frontend_image     = var.frontend_image
# }

output "environment" {
  value = "dev"
}
