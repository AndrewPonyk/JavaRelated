# Terraform Main Configuration
# E-Commerce Platform on AWS ECS

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket         = "ecommerce-terraform-state"
    key            = "infrastructure/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "ecommerce-terraform-locks"
  }
}

# Provider configuration
provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "ecommerce-platform"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

# Data sources
data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

# Local variables
locals {
  name_prefix = "${var.project_name}-${var.environment}"
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# Module: VPC
module "vpc" {
  source = "./modules/vpc"

  name_prefix = local.name_prefix
  cidr_block  = var.vpc_cidr
  az_count    = var.az_count

  common_tags = local.common_tags
}

# Module: ECS Cluster
module "ecs" {
  source = "./modules/ecs"

  name_prefix   = local.name_prefix
  vpc_id        = module.vpc.vpc_id
  subnet_ids    = module.vpc.private_subnet_ids
  cluster_size  = var.ecs_cluster_size

  common_tags = local.common_tags
}

# Module: Application Load Balancer
module "alb" {
  source = "./modules/alb"

  name_prefix    = local.name_prefix
  vpc_id         = module.vpc.vpc_id
  subnet_ids     = module.vpc.public_subnet_ids
  certificate_arn = var.ssl_certificate_arn

  common_tags = local.common_tags
}

# Module: RDS PostgreSQL
module "rds" {
  source = "./modules/rds"

  name_prefix     = local.name_prefix
  vpc_id          = module.vpc.vpc_id
  subnet_ids      = module.vpc.database_subnet_ids
  database_name   = var.database_name
  database_user   = var.database_user
  instance_class  = var.database_instance_class
  multi_az        = var.environment == "production"

  common_tags = local.common_tags
}

# Module: ElastiCache Redis
module "elasticache" {
  source = "./modules/elasticache"

  name_prefix    = local.name_prefix
  vpc_id         = module.vpc.vpc_id
  subnet_ids     = module.vpc.database_subnet_ids
  node_count     = var.redis_node_count
  instance_type  = var.redis_instance_type

  common_tags = local.common_tags
}

# Module: Elasticsearch Domain
module "elasticsearch" {
  source = "./modules/elasticsearch"

  name_prefix   = local.name_prefix
  vpc_id        = module.vpc.vpc_id
  subnet_ids    = module.vpc.private_subnet_ids
  instance_type = var.es_instance_type
  instance_count = var.es_instance_count

  common_tags = local.common_tags
}

# ECS Task Definitions
resource "aws_ecs_task_definition" "backend" {
  family                   = "${local.name_prefix}-backend"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.backend_cpu
  memory                   = var.backend_memory
  execution_role_arn       = module.ecs.task_execution_role_arn
  task_role_arn            = module.ecs.task_role_arn

  container_definitions = jsonencode([
    {
      name      = "backend"
      image     = "${var.backend_repository_url}:${var.image_tag}"
      cpu       = var.backend_cpu
      memory    = var.backend_memory
      essential = true

      port_mappings = [
        {
          containerPort = 8000
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "DJANGO_SETTINGS_MODULE"
          value = "backend.core.config.settings"
        }
      ]

      secrets = [
        {
          name      = "DATABASE_URL"
          valueFrom = aws_secretsmanager_secret.database_url.arn
        },
        {
          name      = "SECRET_KEY"
          valueFrom = aws_secretsmanager_secret.secret_key.arn
        }
      ]

      log_configuration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.ecs_logs.name
          "awslogs-region"        = data.aws_region.current.name
          "awslogs-stream-prefix" = "backend"
        }
      }

      health_check = {
        command     = ["CMD-SHELL", "curl -f http://localhost:8000/api/v1/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }
    }
  ])

  tags = local.common_tags
}

# ECS Service
resource "aws_ecs_service" "backend" {
  name            = "${local.name_prefix}-backend"
  cluster         = module.ecs.cluster_id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = var.backend_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = module.vpc.private_subnet_ids
    security_groups   = [module.ecs.security_group_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = module.alb.backend_target_group_arn
    container_name   = "backend"
    container_port   = 8000
  }

  tags = local.common_tags
}

# CloudWatch Log Group
resource "aws_cloudwatch_log_group" "ecs_logs" {
  name              = "/ecs/${local.name_prefix}"
  retention_in_days = var.environment == "production" ? 30 : 7

  tags = local.common_tags
}

# Secrets Manager
resource "aws_secretsmanager_secret" "database_url" {
  name = "${local.name_prefix}/database/url"
}

resource "aws_secretsmanager_secret" "secret_key" {
  name = "${local.name_prefix}/django/secret_key"
}

# Outputs
output "alb_dns_name" {
  value = module.alb.dns_name
}

output "ecs_cluster_id" {
  value = module.ecs.cluster_id
}

output "database_endpoint" {
  value     = module.rds.endpoint
  sensitive = true
}
