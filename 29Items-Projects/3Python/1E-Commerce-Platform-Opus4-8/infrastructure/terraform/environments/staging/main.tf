# Staging environment — same modules as production, smaller scale.
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
    key            = "staging/terraform.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "ecommerce-tflock"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region
}

module "web" {
  source        = "../../modules/ecs"
  environment   = "staging"
  service_name  = "web"
  image         = var.web_image
  cpu           = 512
  memory        = 1024
  desired_count = 1
}

variable "aws_region" {
  type    = string
  default = "eu-central-1"
}

variable "web_image" {
  type = string
}
