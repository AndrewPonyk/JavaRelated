# Core data-plane resources: S3 for raw documents + SQS for the ingestion pipeline.
# Compute (ECS/ALB/RDS) and SaaS wiring (Pinecone/LangSmith via Secrets Manager) are
# stubbed as TODOs to keep this scaffold focused.

locals {
  name_prefix = "docintel-${var.environment}"
}

# ── S3: raw documents (encrypted, private) ────────────────────────────────
resource "aws_s3_bucket" "documents" {
  bucket = "${var.documents_bucket_name}-${var.environment}"
}

resource "aws_s3_bucket_public_access_block" "documents" {
  bucket                  = aws_s3_bucket.documents.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "aws:kms" # use a restricted CMK for medical/PHI corpora
    }
  }
}

# ── SQS: ingestion job queue + dead-letter queue ──────────────────────────
resource "aws_sqs_queue" "ingestion_dlq" {
  name = "${local.name_prefix}-ingestion-dlq"
}

resource "aws_sqs_queue" "ingestion" {
  name                       = "${local.name_prefix}-ingestion"
  visibility_timeout_seconds = 300 # > worst-case ingest time
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.ingestion_dlq.arn
    maxReceiveCount     = 5
  })
}

# TODO: aws_db_instance (RDS Postgres, Multi-AZ in prod, encrypted)
# TODO: aws_ecs_cluster / aws_ecs_service / aws_lb (API behind ALB)
# TODO: aws_ecs_task_definition for the ingestion worker (same image, worker entrypoint)
# TODO: aws_secretsmanager_secret for PINECONE_API_KEY, LANGSMITH_API_KEY, DB creds
