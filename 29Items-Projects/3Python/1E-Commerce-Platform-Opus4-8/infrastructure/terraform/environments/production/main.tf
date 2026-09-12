# Production environment — composes shared modules at production sizing.
# Remote state lives in S3 with a DynamoDB lock table.

terraform {
  required_version = ">= 1.7"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
  backend "s3" {
    bucket         = "ecommerce-tfstate"
    key            = "production/terraform.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "ecommerce-tflock"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region
}

# TODO: vpc, rds (primary + replica), elasticache modules.

module "web" {
  source        = "../../modules/ecs"
  environment   = "production"
  service_name  = "web"
  image         = var.web_image
  cpu           = 1024
  memory        = 2048
  desired_count = 4
}

module "worker_ml" {
  source        = "../../modules/ecs"
  environment   = "production"
  service_name  = "worker-ml"
  image         = var.web_image
  cpu           = 2048
  memory        = 8192
  desired_count = 1
  container_port = 0
}
