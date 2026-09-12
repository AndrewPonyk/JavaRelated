# ECS Fargate service for analytics-api (rolling deploy behind an ALB).
# Skeleton: cluster + task definition are real; ALB/service/autoscaling are TODO
# so the module stays reviewable while the network edge design lands.

variable "name" { type = string }
variable "image" {
  type        = string
  description = "ECR image URI incl. tag (git SHA — set by cd-deploy.yml)"
}
variable "cpu" {
  type    = number
  default = 512
}
variable "memory" {
  type    = number
  default = 1024
}
variable "environment" {
  type        = map(string)
  description = "Plain env vars (non-secret); secrets arrive via SSM/Secrets references"
  default     = {}
}
variable "tags" {
  type    = map(string)
  default = {}
}

resource "aws_ecs_cluster" "this" {
  name = var.name
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
  tags = var.tags
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/${var.name}"
  retention_in_days = 30
  tags              = var.tags
}

resource "aws_iam_role" "task_execution" {
  name = "${var.name}-task-exec"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "task_execution" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_ecs_task_definition" "api" {
  family                   = var.name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = aws_iam_role.task_execution.arn
  # TODO: task_role_arn with MSK-IAM consume + Secrets Manager read for the app itself

  container_definitions = jsonencode([{
    name         = "analytics-api"
    image        = var.image
    essential    = true
    portMappings = [{ containerPort = 8080, protocol = "tcp" }]
    environment  = [for k, v in var.environment : { name = k, value = v }]
    # TODO: "secrets" entries referencing SSM/Secrets Manager ARNs (POSTGRES_PASSWORD…)
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.api.name
        awslogs-region        = "eu-central-1" # TODO: var
        awslogs-stream-prefix = "api"
      }
    }
  }])

  tags = var.tags
}

# TODO(phase-2):
#   aws_lb + aws_lb_target_group (health check /actuator/health, deregistration_delay
#   low — SSE clients reconnect) + aws_lb_listener (HTTPS, ACM cert)
#   aws_ecs_service (desired_count ≥ 2, deployment 100%/200%)
#   aws_appautoscaling_* on CPU + ALB request count
#   WAF on the ALB; CloudFront in front for the SPA + /api/* routing

output "cluster_arn" { value = aws_ecs_cluster.this.arn }
output "task_definition_arn" { value = aws_ecs_task_definition.api.arn }
