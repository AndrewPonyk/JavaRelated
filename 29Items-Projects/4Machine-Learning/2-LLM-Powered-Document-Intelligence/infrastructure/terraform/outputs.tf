output "documents_bucket" {
  description = "S3 bucket holding raw uploaded documents."
  value       = aws_s3_bucket.documents.id
}

output "ingestion_queue_url" {
  description = "SQS queue URL for ingestion jobs."
  value       = aws_sqs_queue.ingestion.url
}

output "app_task_role_arn" {
  description = "IAM role assumed by the API + worker (has Bedrock invoke permission)."
  value       = aws_iam_role.app_task.arn
}

output "allowed_bedrock_models" {
  description = "Bedrock model IDs the app task role may invoke."
  value       = var.bedrock_model_ids
}
