output "dags_bucket" {
  description = "Bucket CD syncs airflow/dags into"
  value       = aws_s3_bucket.mwaa.bucket
}

output "webserver_url" {
  value = var.enabled ? aws_mwaa_environment.this[0].webserver_url : null
}
