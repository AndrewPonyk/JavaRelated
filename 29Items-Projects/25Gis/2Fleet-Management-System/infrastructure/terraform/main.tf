terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

variable "aws_region" {
  description = "AWS region for the fleet management infrastructure."
  type        = string
  default     = "us-east-1"
}

resource "aws_ecs_cluster" "fleet" {
  name = "fleet-management"
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/fleet-management-api"
  retention_in_days = 30
}

resource "aws_ecr_repository" "backend" {
  name                 = "fleet-management-backend"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_repository" "frontend" {
  name                 = "fleet-management-frontend"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.fleet.name
}

output "backend_repository_url" {
  value = aws_ecr_repository.backend.repository_url
}
