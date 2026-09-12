# Lake storage: one bucket per medallion layer + artifacts.
# Versioning (accidental-delete safety net), SSE (KMS when a key is supplied),
# TLS-only bucket policies, full public-access block, Bronze lifecycle tiering.

variable "project" { type = string }
variable "environment" { type = string }

variable "kms_key_arn" {
  description = "KMS key for bucket encryption; empty string selects SSE-S3 (AES256)"
  type        = string
  default     = ""
}

locals {
  layers = ["bronze", "silver", "gold", "artifacts"]
}

resource "aws_s3_bucket" "layer" {
  for_each = toset(local.layers)
  bucket   = "${var.environment}-${var.project}-${each.key}"
}

resource "aws_s3_bucket_versioning" "layer" {
  for_each = aws_s3_bucket.layer
  bucket   = each.value.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "layer" {
  for_each = aws_s3_bucket.layer
  bucket   = each.value.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = var.kms_key_arn == "" ? "AES256" : "aws:kms"
      kms_master_key_id = var.kms_key_arn == "" ? null : var.kms_key_arn
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "layer" {
  for_each = aws_s3_bucket.layer
  bucket   = each.value.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Encryption in transit is not optional: deny any non-TLS access outright.
resource "aws_s3_bucket_policy" "deny_insecure_transport" {
  for_each = aws_s3_bucket.layer
  bucket   = each.value.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource  = [each.value.arn, "${each.value.arn}/*"]
      Condition = {
        Bool = { "aws:SecureTransport" = "false" }
      }
    }]
  })

  depends_on = [aws_s3_bucket_public_access_block.layer]
}

# Bronze is the replayable archive: cheap storage tiers as it ages.
resource "aws_s3_bucket_lifecycle_configuration" "bronze" {
  bucket = aws_s3_bucket.layer["bronze"].id

  rule {
    id     = "bronze-tiering"
    status = "Enabled"

    filter {} # whole bucket

    transition {
      days          = 90
      storage_class = "STANDARD_IA"
    }

    transition {
      days          = 365
      storage_class = "GLACIER"
    }
  }
}

output "bucket_names" {
  value = { for layer, bucket in aws_s3_bucket.layer : layer => bucket.bucket }
}

output "bucket_arns" {
  value = { for layer, bucket in aws_s3_bucket.layer : layer => bucket.arn }
}
