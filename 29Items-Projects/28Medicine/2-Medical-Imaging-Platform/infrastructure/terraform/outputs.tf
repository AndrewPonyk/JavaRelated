output "dicom_archive_bucket" {
  description = "S3 bucket holding the DICOM archive."
  value       = aws_s3_bucket.dicom_archive.id
}

output "kms_key_arn" {
  description = "Customer-managed KMS key ARN."
  value       = aws_kms_key.main.arn
}

output "ingest_queue_url" {
  description = "SQS ingest queue URL."
  value       = aws_sqs_queue.ingest.url
}

# TODO: output ALB DNS name, RDS endpoint, Cognito pool id, ECR repo URLs.
