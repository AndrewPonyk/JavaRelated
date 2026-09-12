terraform {
  required_version = ">= 1.7"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
  }

  # Remote state — configure per environment (S3 + DynamoDB lock).
  # backend "s3" {
  #   bucket         = "docintel-tfstate"
  #   key            = "env/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "docintel-tflock"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "doc-intelligence"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
