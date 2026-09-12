# ECS Fargate — the three stateless services (gateway / api / alerting).

resource "aws_ecs_cluster" "this" {
  name = local.name_prefix

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_ecr_repository" "app" {
  name = "log-analytics-app"
  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_repository" "spark" {
  name = "log-analytics-spark"
}

# ── Service pattern (repeat for gateway / api / alerting) ─────────────────────
# TODO(Phase 2): extract into a module; wiring sketched here for the reader.
#
# resource "aws_ecs_task_definition" "api" {
#   family                   = "${local.name_prefix}-api"
#   requires_compatibilities = ["FARGATE"]
#   cpu                      = 512
#   memory                   = 1024
#   network_mode             = "awsvpc"
#   execution_role_arn       = aws_iam_role.task_execution.arn
#   task_role_arn            = aws_iam_role.api_task.arn
#   container_definitions = jsonencode([{
#     name  = "api"
#     image = "${aws_ecr_repository.app.repository_url}:latest"   # tag injected by deploy.yml
#     portMappings = [{ containerPort = 8000 }]
#     environment = [
#       { name = "LA_APP_ENV",                 value = var.environment },
#       { name = "LA_KAFKA_BOOTSTRAP_SERVERS", value = aws_msk_cluster.logs.bootstrap_brokers_tls },
#       { name = "LA_OPENSEARCH_URL",          value = "https://${aws_opensearch_domain.logs.endpoint}" },
#     ]
#     secrets = [
#       { name = "LA_SLACK_WEBHOOK_URL", valueFrom = "<secretsmanager-arn>" },  # TODO
#     ]
#     logConfiguration = {
#       logDriver = "awslogs"
#       options = { awslogs-group = "/ecs/${local.name_prefix}", awslogs-region = var.region, awslogs-stream-prefix = "api" }
#     }
#   }])
# }
#
# resource "aws_ecs_service" "api" { … desired_count = 2, ALB target group, autoscaling … }
#
# gateway: same image, command override → uvicorn log_analytics.ingestion.gateway:app --port 8080
# alerting: same image, command override → python -m log_analytics.alerting.engine (no ALB)
