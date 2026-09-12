# Amazon Managed Service for Apache Flink — one application per job (ADR #2).
# IMPORTANT: checkpoint settings HERE override anything the job sets in code
# (docs/TECH-NOTES.md §3.6.6). Deploys are savepoint-based (stop → update jar → restore).

variable "name" { type = string }
variable "runtime_environment" {
  type    = string
  default = "FLINK-1_20" # TODO: verify against currently supported runtimes
}
variable "artifacts_bucket_arn" { type = string }
variable "jar_s3_key" {
  type        = string
  description = "S3 key of the shaded job jar (written by cd-deploy.yml, keyed by git SHA)"
}
variable "parallelism" {
  type    = number
  default = 4
}
variable "checkpoint_interval_ms" {
  type    = number
  default = 5000 # prod hot-path budget: alert freshness ≈ checkpoint interval (ARCHITECTURE §2.3)
}
variable "runtime_properties" {
  type        = map(string)
  description = "Flink runtime property group 'kafka' (bootstrap servers etc.) read by KafkaConfig"
  default     = {}
}
variable "subnet_ids" { type = list(string) }
variable "security_group_ids" { type = list(string) }
variable "tags" {
  type    = map(string)
  default = {}
}

resource "aws_iam_role" "flink" {
  name = "${var.name}-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "kinesisanalytics.amazonaws.com" }
    }]
  })
  tags = var.tags
}

# TODO(security): replace with least-privilege policies —
#   S3 read on the artifacts bucket, kafka-cluster:* scoped to this cluster/topics
#   (per-topic IAM), CloudWatch logs/metrics write.
resource "aws_iam_role_policy" "flink_stub" {
  name = "${var.name}-stub"
  role = aws_iam_role.flink.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:GetObject"]
      Resource = ["${var.artifacts_bucket_arn}/*"]
    }]
  })
}

resource "aws_kinesisanalyticsv2_application" "this" {
  name                   = var.name
  runtime_environment    = var.runtime_environment
  service_execution_role = aws_iam_role.flink.arn

  application_configuration {
    application_code_configuration {
      code_content {
        s3_content_location {
          bucket_arn = var.artifacts_bucket_arn
          file_key   = var.jar_s3_key
        }
      }
      code_content_type = "ZIPFILE"
    }

    application_snapshot_configuration {
      snapshots_enabled = true # savepoint-based deploys depend on this
    }

    flink_application_configuration {
      checkpoint_configuration {
        configuration_type            = "CUSTOM"
        checkpointing_enabled         = true
        checkpoint_interval           = var.checkpoint_interval_ms
        min_pause_between_checkpoints = 5000
      }
      monitoring_configuration {
        configuration_type = "CUSTOM"
        log_level          = "INFO"
        metrics_level      = "TASK"
      }
      parallelism_configuration {
        configuration_type   = "CUSTOM"
        parallelism          = var.parallelism
        parallelism_per_kpu  = 1
        auto_scaling_enabled = true
      }
    }

    environment_properties {
      property_group {
        property_group_id = "kafka"
        property_map      = var.runtime_properties
      }
    }

    vpc_configuration {
      subnet_ids         = var.subnet_ids
      security_group_ids = var.security_group_ids
    }
  }

  tags = var.tags
}

output "application_arn" { value = aws_kinesisanalyticsv2_application.this.arn }
