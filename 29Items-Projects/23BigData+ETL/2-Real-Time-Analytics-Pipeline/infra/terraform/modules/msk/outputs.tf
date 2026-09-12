output "cluster_arn" {
  value = aws_msk_cluster.this.arn
}

output "bootstrap_brokers_sasl_iam" {
  description = "Bootstrap string for IAM-authenticated clients (port 9098)"
  value       = aws_msk_cluster.this.bootstrap_brokers_sasl_iam
}
