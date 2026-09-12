# Airflow (MWAA) + the S3 bucket CD syncs DAGs into.
# MWAA is expensive and slow to (re)create — dev keeps enabled=false and runs
# Airflow via docker compose; staging/prod enable it.

resource "aws_s3_bucket" "mwaa" {
  bucket = "${var.name}-mwaa"
  tags   = var.tags
}

resource "aws_s3_bucket_versioning" "mwaa" {
  bucket = aws_s3_bucket.mwaa.id
  versioning_configuration {
    status = "Enabled" # MWAA requires versioning on the source bucket
  }
}

resource "aws_s3_bucket_public_access_block" "mwaa" {
  bucket                  = aws_s3_bucket.mwaa.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_iam_role" "mwaa_execution" {
  count = var.enabled ? 1 : 0
  name  = "${var.name}-mwaa-execution"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = ["airflow.amazonaws.com", "airflow-env.amazonaws.com"] }
    }]
  })

  tags = var.tags
}

# TODO(Phase 1): attach the documented MWAA execution policy (S3 dags bucket,
# CloudWatch, SQS, KMS) + Glue StartJobRun, Snowflake secret read, SNS publish,
# ECS RunTask for the dbt container.

resource "aws_mwaa_environment" "this" {
  count = var.enabled ? 1 : 0

  name               = var.name
  airflow_version    = "2.9.2"
  environment_class  = var.environment_class
  execution_role_arn = aws_iam_role.mwaa_execution[0].arn

  source_bucket_arn = aws_s3_bucket.mwaa.arn
  dag_s3_path       = "dags"
  # requirements_s3_path = "requirements.txt"   # TODO: upload airflow/requirements.txt in CD

  network_configuration {
    security_group_ids = var.security_group_ids
    subnet_ids         = slice(var.subnet_ids, 0, 2) # MWAA wants exactly 2 subnets
  }

  webserver_access_mode = "PRIVATE_ONLY"

  airflow_configuration_options = {
    "core.default_task_retries" = "2"
    "core.load_examples"        = "False"
  }

  tags = var.tags
}
