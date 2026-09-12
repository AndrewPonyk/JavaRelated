output "msk_bootstrap_brokers_tls" {
  description = "Value for LA_KAFKA_BOOTSTRAP_SERVERS in AWS environments"
  value       = aws_msk_cluster.logs.bootstrap_brokers_tls
}

output "opensearch_endpoint" {
  description = "Value for LA_OPENSEARCH_URL (https://…)"
  value       = "https://${aws_opensearch_domain.logs.endpoint}"
}

output "opensearch_dashboards_endpoint" {
  value = "https://${aws_opensearch_domain.logs.dashboard_endpoint}"
}

output "emr_application_id" {
  description = "Set as EMR_APP_ID GitHub variable (deploy.yml)"
  value       = aws_emrserverless_application.spark.id
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "artifacts_bucket" {
  description = "Set as ARTIFACTS_BUCKET GitHub variable (Spark code artifacts)"
  value       = aws_s3_bucket.artifacts.bucket
}

output "ci_role_arn" {
  description = "Set as AWS_CI_ROLE_ARN GitHub variable (ci.yml image pushes)"
  value       = aws_iam_role.github_ci.arn
}

output "deploy_role_arn" {
  description = "Set as AWS_DEPLOY_ROLE_ARN per-environment GitHub variable (deploy.yml)"
  value       = aws_iam_role.github_deploy.arn
}
