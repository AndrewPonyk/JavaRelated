# Reusable ECS service module. Composed per environment with different sizing.
# This is an illustrative stub — flesh out task definitions, autoscaling,
# CodeDeploy blue/green, and IAM roles as the platform matures.

variable "environment" { type = string }
variable "service_name" { type = string }
variable "image" { type = string }

variable "cpu" {
  type    = number
  default = 512
}

variable "memory" {
  type    = number
  default = 1024
}

variable "desired_count" {
  type    = number
  default = 2
}

variable "container_port" {
  type    = number
  default = 8000
}

resource "aws_ecs_cluster" "this" {
  name = "${var.environment}-ecommerce"
}

resource "aws_ecs_task_definition" "this" {
  family                   = "${var.environment}-${var.service_name}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.cpu
  memory                   = var.memory

  # TODO: inject secrets from Secrets Manager via `secrets` block; set
  # execution/task IAM roles with least privilege.
  container_definitions = jsonencode([
    {
      name      = var.service_name
      image     = var.image
      essential = true
      portMappings = [{ containerPort = var.container_port }]
    }
  ])
}

resource "aws_ecs_service" "this" {
  name            = var.service_name
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.this.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"
  # TODO: load_balancer block, network_configuration, autoscaling target.
}

output "cluster_arn" { value = aws_ecs_cluster.this.arn }
output "service_name" { value = aws_ecs_service.this.name }
