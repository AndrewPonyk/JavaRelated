output "lake_bucket" {
  value = aws_s3_bucket.lake.bucket
}

output "artifacts_bucket" {
  value = aws_s3_bucket.artifacts.bucket
}

output "glue_job_name" {
  value = aws_glue_job.raw_events_to_parquet.name
}

output "glue_catalog_database" {
  value = aws_glue_catalog_database.lake.name
}
