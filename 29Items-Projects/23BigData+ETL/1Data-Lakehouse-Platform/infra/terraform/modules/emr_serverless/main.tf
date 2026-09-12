# Spark runtime: EMR Serverless — per-job autoscaling, scale-to-zero,
# no cluster to babysit. Jobs are submitted by Airflow (StartJobRun).

variable "project" { type = string }
variable "environment" { type = string }

variable "lake_bucket_arns" {
  description = "ARNs of the bronze/silver/gold buckets (read-write for jobs)"
  type        = list(string)
}

variable "artifacts_bucket_arn" {
  description = "ARN of the artifacts bucket (wheels/entrypoints read; metrics/checkpoints write)"
  type        = string
}

resource "aws_emrserverless_application" "spark" {
  name          = "${var.environment}-${var.project}-spark"
  release_label = "emr-7.1.0" # Spark 3.5.x — keep in lockstep with data-platform/pyproject.toml
  type          = "spark"

  maximum_capacity {
    cpu    = "64 vCPU"
    memory = "512 GB"
  }

  auto_stop_configuration {
    enabled              = true
    idle_timeout_minutes = 5
  }
}

resource "aws_iam_role" "job_execution" {
  name = "${var.environment}-${var.project}-emr-job"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "emr-serverless.amazonaws.com" }
    }]
  })
}

# Least privilege: rw on lake data, rw on artifacts (metrics/checkpoints live
# there), Glue catalog for table registration, CloudWatch logs. No wildcards
# beyond the platform's own buckets.
resource "aws_iam_role_policy" "job_permissions" {
  name = "lakehouse-job-permissions"
  role = aws_iam_role.job_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "LakeData"
        Effect = "Allow"
        Action = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:ListBucket"]
        Resource = concat(
          var.lake_bucket_arns,
          [for arn in var.lake_bucket_arns : "${arn}/*"],
          [var.artifacts_bucket_arn, "${var.artifacts_bucket_arn}/*"],
        )
      },
      {
        Sid    = "GlueCatalog"
        Effect = "Allow"
        Action = [
          "glue:GetDatabase", "glue:GetDatabases", "glue:CreateDatabase",
          "glue:GetTable", "glue:GetTables", "glue:CreateTable", "glue:UpdateTable",
          "glue:GetPartitions", "glue:BatchCreatePartition",
        ]
        Resource = "*"
      },
      {
        Sid      = "Logs"
        Effect   = "Allow"
        Action   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "arn:aws:logs:*:*:log-group:/aws/emr-serverless/*"
      },
    ]
  })
}

output "application_id" {
  value = aws_emrserverless_application.spark.id
}

output "job_execution_role_arn" {
  value = aws_iam_role.job_execution.arn
}
