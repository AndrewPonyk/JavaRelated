# Speed-layer runtime: ECS cluster + ECR repos + ElastiCache Redis hot store.

resource "aws_ecs_cluster" "this" {
  name = var.name

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = var.tags
}

resource "aws_ecr_repository" "services" {
  for_each = toset(["api", "processor", "frontend"])

  name                 = "${var.name}/${each.key}"
  image_tag_mutability = "IMMUTABLE" # deploys are SHA-tagged artifact promotions

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = var.tags
}

resource "aws_security_group" "redis" {
  name_prefix = "${var.name}-redis-"
  vpc_id      = var.vpc_id

  ingress {
    description = "Redis from inside the VPC (API + processor tasks)"
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    cidr_blocks = [var.client_cidr]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = var.tags
}

resource "aws_elasticache_subnet_group" "redis" {
  name       = "${var.name}-redis"
  subnet_ids = var.subnet_ids
}

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id = "${var.name}-hot-store"
  description          = "Hot metric store + pub/sub fan-out to the API"

  engine               = "redis"
  engine_version       = "7.1"
  node_type            = var.redis_node_type
  num_cache_clusters   = var.redis_replicas + 1
  port                 = 6379
  subnet_group_name    = aws_elasticache_subnet_group.redis.name
  security_group_ids   = [aws_security_group.redis.id]
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true

  tags = var.tags
}

resource "aws_cloudwatch_log_group" "services" {
  for_each          = toset(["api", "processor"])
  name              = "/ecs/${var.name}/${each.key}"
  retention_in_days = 30
  tags              = var.tags
}

# TODO(Phase 1):
#  - task definitions + services for api (behind ALB) and processor (no LB),
#    task roles with MSK IAM auth (kafka-cluster:* on the cluster ARN) and
#    Secrets Manager read for Snowflake creds
#  - ALB + target group + HTTPS listener (ACM cert)
#  - processor autoscaling policy on MSK MaxOffsetLag (CloudWatch)
