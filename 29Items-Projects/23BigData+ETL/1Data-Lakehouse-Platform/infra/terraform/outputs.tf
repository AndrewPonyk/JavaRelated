output "lake_bucket_names" {
  description = "Per-layer lake bucket names"
  value       = module.lake_storage.bucket_names
}

output "emr_application_id" {
  description = "EMR Serverless application id for job submission"
  value       = module.processing.application_id
}

output "kafka_bootstrap_brokers" {
  description = "MSK bootstrap brokers (SASL/IAM)"
  value       = module.streaming.bootstrap_brokers
  sensitive   = true
}
