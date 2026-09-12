# Terraform — AWS infrastructure for the Medical Imaging Platform (HIPAA).
# All services used are HIPAA-eligible and covered by a signed AWS BAA.
# This is a scaffold: resources are sketched with the security-relevant
# settings called out. Fill in modules/args before applying.

terraform {
  required_version = ">= 1.7"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.50"
    }
  }
  # TODO: remote state with locking
  # backend "s3" { bucket = "tfstate-medimaging"; key = "prod/terraform.tfstate"; dynamodb_table = "tf-lock"; encrypt = true }
}

provider "aws" {
  region = var.aws_region
  default_tags {
    tags = {
      Project    = "medical-imaging-platform"
      Compliance = "HIPAA"
      ManagedBy  = "terraform"
      Env        = var.environment
    }
  }
}

# ── KMS: customer-managed keys for envelope encryption ───────
resource "aws_kms_key" "main" {
  description             = "medimaging-${var.environment} CMK"
  enable_key_rotation     = true
  deletion_window_in_days = 30
}

# ── Networking (private subnets for compute + data) ──────────
# TODO: VPC, public/private subnets across >=2 AZs, NAT, flow logs.
# module "vpc" { source = "terraform-aws-modules/vpc/aws" ... }

# ── S3: DICOM archive (encrypted, versioned, lifecycle) ──────
resource "aws_s3_bucket" "dicom_archive" {
  bucket = "medimaging-${var.environment}-dicom-archive"
}

resource "aws_s3_bucket_server_side_encryption_configuration" "dicom_archive" {
  bucket = aws_s3_bucket.dicom_archive.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.main.arn
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_versioning" "dicom_archive" {
  bucket = aws_s3_bucket.dicom_archive.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_public_access_block" "dicom_archive" {
  bucket                  = aws_s3_bucket.dicom_archive.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "dicom_archive" {
  bucket = aws_s3_bucket.dicom_archive.id
  rule {
    id     = "cold-archive"
    status = "Enabled"
    transition {
      days          = 90
      storage_class = "INTELLIGENT_TIERING"
    }
    transition {
      days          = 365
      storage_class = "GLACIER"
    }
  }
}

# ── RDS PostgreSQL (encrypted, Multi-AZ, private) ────────────
# TODO: aws_db_subnet_group + aws_db_instance
#   storage_encrypted = true, kms_key_id = aws_kms_key.main.arn
#   multi_az = true, publicly_accessible = false, deletion_protection = true
#   backup_retention_period >= 7, performance_insights_enabled = true

# ── SQS: ingest + inference queues with DLQs ─────────────────
resource "aws_sqs_queue" "ingest_dlq" {
  name = "medimaging-${var.environment}-ingest-dlq"
}

resource "aws_sqs_queue" "ingest" {
  name                       = "medimaging-${var.environment}-ingest"
  visibility_timeout_seconds = 300
  kms_master_key_id          = aws_kms_key.main.id
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.ingest_dlq.arn
    maxReceiveCount     = 5
  })
}

# ── ECS Fargate cluster + services (api, worker, ml) ─────────
# TODO: aws_ecs_cluster, task definitions (non-root, read-only rootfs),
#   services behind the ALB, autoscaling (CPU + SQS depth), per-task IAM roles.

# ── ALB + WAF + ACM ──────────────────────────────────────────
# TODO: aws_lb (HTTPS only), aws_acm_certificate, aws_wafv2_web_acl (OWASP rules).

# ── Cognito (AuthN) ──────────────────────────────────────────
# TODO: aws_cognito_user_pool with MFA, hosted UI / OIDC federation to hospital SSO.

# ── Audit/Logging ────────────────────────────────────────────
# TODO: aws_cloudtrail (org trail), CloudWatch log groups (KMS-encrypted),
#   GuardDuty, Config rules for HIPAA conformance pack.
