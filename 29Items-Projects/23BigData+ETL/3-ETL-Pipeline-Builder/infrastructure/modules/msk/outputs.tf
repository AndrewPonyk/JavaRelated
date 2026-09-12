output "cluster_arn" {
  value = aws_msk_cluster.this.arn
}

output "bootstrap_brokers_sasl_iam" {
  description = "Bootstrap string for KAFKA_BOOTSTRAP_SERVERS in cloud envs"
  value       = aws_msk_cluster.this.bootstrap_brokers_sasl_iam
}

output "security_group_id" {
  value = aws_security_group.msk.id
}
