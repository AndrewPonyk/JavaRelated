# S3 data lake (raw/staged/curated prefixes), artifacts bucket, Glue catalog + jobs.

resource "aws_s3_bucket" "lake" {
  bucket = "${var.name}-lake"
  tags   = var.tags
}

resource "aws_s3_bucket" "artifacts" {
  bucket = "${var.name}-artifacts" # Glue scripts, ML models, GE data docs, dbt docs
  tags   = var.tags
}

resource "aws_s3_bucket_versioning" "lake" {
  bucket = aws_s3_bucket.lake.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "lake" {
  bucket                  = aws_s3_bucket.lake.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_public_access_block" "artifacts" {
  bucket                  = aws_s3_bucket.artifacts.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_glue_catalog_database" "lake" {
  name = replace("${var.name}_lake", "-", "_")
}

resource "aws_iam_role" "glue" {
  name = "${var.name}-glue-job"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "glue.amazonaws.com" }
    }]
  })

  tags = var.tags
}

# TODO(Phase 1): replace with a least-privilege inline policy
# (lake bucket rw on raw/+staged/, artifacts read, catalog, CloudWatch logs).
resource "aws_iam_role_policy_attachment" "glue_service" {
  role       = aws_iam_role.glue.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSGlueServiceRole"
}

resource "aws_glue_job" "raw_events_to_parquet" {
  name         = "${var.env}-raw-events-to-parquet" # referenced by etl_daily_batch_dag
  role_arn     = aws_iam_role.glue.arn
  glue_version = "4.0"

  command {
    name            = "glueetl"
    python_version  = "3"
    script_location = "s3://${aws_s3_bucket.artifacts.bucket}/glue/raw_events_to_parquet.py"
  }

  worker_type       = "G.1X"
  number_of_workers = var.glue_workers

  default_arguments = {
    "--job-language"                     = "python"
    "--enable-metrics"                   = "true"
    "--enable-continuous-cloudwatch-log" = "true"
    "--lake_bucket"                      = aws_s3_bucket.lake.bucket
  }

  tags = var.tags
}

# TODO(Phase 2): aws_glue_crawler over staged/ to keep catalog partitions fresh.
